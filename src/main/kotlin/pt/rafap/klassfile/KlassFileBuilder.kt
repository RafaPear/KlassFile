package pt.rafap.klassfile

import com.sun.tools.javac.tree.TreeInfo.flags
import pt.rafap.klassfile.builders.FieldScope
import pt.rafap.klassfile.builders.FlagsScope
import pt.rafap.klassfile.builders.MethodScope
import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.models.InvokeType
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.utils.*
import java.lang.classfile.ClassFile
import java.lang.classfile.ClassFile.ACC_PUBLIC
import java.lang.classfile.ClassFile.ACC_STATIC
import java.lang.classfile.Interfaces
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.*
import java.lang.reflect.Modifier
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance


/**
 * DSL builder for defining a class structure, its members, and its bytecode-level behavior.
 *
 * The builder collects fields, methods, implemented interfaces, and superclass information, then emits a generated
 * class representation through the `klass()` API.
 */
@KlassFileDsl
@Suppress("unused")
class KlassFileBuilder<O : Any> private constructor(
    val name: String,
    inheritor: KClass<O>,
    body: KlassFileBuilder<O>.() -> Unit = {},
) {

    val thisClassDesc = classDesc(name)
    val thisKlassDesc = KlassDesc(thisClassDesc, inheritor)

    private val flagsScope = FlagsScope.ClassFlagsScope(name)
    private val fieldScope = FieldScope(thisKlassDesc)
    private val methodRefs = mutableListOf<MethodRef<*, *>>()
    private var hasNoArgsConstructor: Boolean = false


    init {
        body()
    }

    /////// UTILITIES ///////

    /**
     * Configures the access flags for the generated class using a [FlagsScope.ClassFlagsScope] DSL.
     */
    fun access(builder: FlagsScope.ClassFlagsScope.() -> Unit) = flagsScope.builder()

    /**
     * Registers a field in the generated class with the specified type and access flags.
     *
     * The field name is inferred from the property name in the DSL context.
     * @param T the field type.
     * @param type the [ClassDesc] representing the field type. Defaults to [CD_void].
     * @param access a lambda to configure the field's access flags using [FlagsScope.FieldFlagsScope]. Defaults to `private`.
     */
    fun <T : Any> field(
        type: KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<O, T>> = fieldScope.field(type, access)

    inline fun <reified T : Any> field(
        noinline access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ) = field(klassDescOf<T>(), access)

    fun <R : Any> method(
        name: String,
        type: KlassDesc<R>,
        invokeType: InvokeType = InvokeType.VIRTUAL,
        builder: MethodScope<O, R>.() -> Unit,
    ): MethodRef<O, R> {
        val methodRef = MethodScope(name, thisKlassDesc, type, invokeType, body = builder).build()
        methodRefs.add(methodRef)
        return methodRef
    }

    inline fun <reified R : Any> method(
        name: String,
        invokeType: InvokeType = InvokeType.VIRTUAL,
        noinline builder: MethodScope<O, R>.() -> Unit,
    ) = method(name, klassDescOf<R>(), invokeType, builder)

    fun <R : Any> method(
        type: KlassDesc<R>,
        invokeType: InvokeType = InvokeType.VIRTUAL,
        builder: MethodScope<O, R>.() -> Unit,
    ) = EagerDelegate { _, property ->
        method(property.name, type, invokeType, builder)
    }

    inline fun <reified R : Any> method(
        invokeType: InvokeType = InvokeType.VIRTUAL,
        noinline builder: MethodScope<O, R>.() -> Unit,
    ) = method(klassDescOf<R>(), invokeType, builder)

    /**
     * Declares a constructor for the generated class.
     *
     * If [flags] includes [ACC_STATIC], the generated member is named [CLASS_INIT_NAME] instead of [INIT_NAME],
     * effectively creating a class initializer.
     *
     * @param builder the constructor body. By default, it invokes the superclass constructor and returns.
     * @return a [MethodRef] describing the generated member.
     */
    fun constructor(
        builder: MethodScope<O, Unit>.() -> Unit = { access { public() }; code { defaultCtor(); ret() } },
    ): MethodRef<O, Unit> {
        hasNoArgsConstructor = true
        return method(INIT_NAME, klassDescOf<Unit>(), InvokeType.SPECIAL, builder = builder)
    }

    fun FieldRef<*, *>.genGetterName(): String {
        return "get" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun FieldRef<*, *>.genSetterName(): String {
        return "set" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun <T : Any> getter(
        name: String,
        field: FieldRef<O, T>,
        access: FlagsScope.MethodFlagsScope.() -> Unit = { public() },
    ) = method(
        name,
        type = field.type,
        invokeType = if (field.isStatic) InvokeType.STATIC else InvokeType.VIRTUAL
    ) {
        access { access() }

        code {
            loadReceiver()
            getField(field)
            ret()
        }
    }

    inline fun <reified T : Any> getter(
        field: FieldRef<O, T>,
        noinline access: FlagsScope.MethodFlagsScope.() -> Unit = { public() },
    ) = EagerDelegate { _, _ ->
        getter(field.genGetterName(), field, access)
    }

    fun <T : Any> setter(
        name: String,
        field: FieldRef<O, T>,
        access: FlagsScope.MethodFlagsScope.() -> Unit = { public() },
    ) = method<Unit>(name, invokeType = if (field.isStatic) InvokeType.STATIC else InvokeType.VIRTUAL) {
        val value by parameter(field.type)

        access { access() }

        code {
            putThisField(field) { load(value) }
            ret()
        }
    }

    inline fun <reified T : Any> setter(
        field: FieldRef<O, T>,
        noinline access: FlagsScope.MethodFlagsScope.() -> Unit = { public() },
    ) = EagerDelegate { _, _ ->
        setter(field.genSetterName(), field, access)
    }

    /////// BUILDERS AND LOADERS ///////
    /**
     * Wrapper around the generated class bytes and the most common ways to consume them.
     *
     * Instances of this class are created only after the builder has been fully configured; they expose the final
     * byte array together with helpers to write it, load it, or instantiate it.
     */
    inner class Klass {
        /** Raw bytecode for the generated class. */
        val bytes: ByteArray = build()

        /**
         * Writes the generated bytes to disk and returns the loaded [KClass].
         *
         * The class is written under the builder's [name] before being loaded.
         *
         * @param T the desired Kotlin type for the returned class.
         * @return the loaded [KClass] instance.
         */
        fun writeAndGetClass(): KClass<O> = writeAndGetClass(name, bytes)

        /**
         * Writes the generated bytes to disk and creates a new instance of the generated class.
         *
         * This requires the generated class to expose a public no-argument constructor compatible with
         * [createInstance].
         *
         * @param T the expected type of the generated instance.
         * @return a new instance of the generated class.
         */
        fun writeAndGetInstance(): O {
            val kClass = writeAndGetClass()

            val className = kClass.simpleName ?: "Unknown"

            when {
                !kClass.constructors.any { it.parameters.isEmpty() } -> throw NoConstructorError(className)
                kClass.isAbstract -> throw AbstractInstanceError(className)
                kClass.java.isEnum -> throw EnumInstanceError(className)
                kClass.java.isInterface -> throw InterfaceInstanceError(className)
            }

            return kClass.createInstance()
        }

        /**
         * Loads the generated class without writing it to disk.
         *
         * This uses the builder's [name] and does not persist the generated bytes.
         *
         * @param T the expected type of the generated class.
         * @return the loaded class reference.
         */
        inline fun <reified T : Any> load() = loadClass<T>(name)

        /** Writes the generated class bytes to disk under the builder's [name]. */
        fun writeToDisk() = write(name, bytes)
    }

    /**
     * Builds the class and returns a [Klass] wrapper around the generated bytes.
     *
     * This is the main terminal step of the DSL: it finalizes the collected fields, methods, superclass, and
     * interfaces into a byte array.
     *
     * @return the generated class wrapper.
     */
    fun klass(): Klass = Klass()


    private fun checkMethodImplementations() {
        val kClass = thisKlassDesc.kClass

        val required = kClass.java.methods
            .filter { method -> Modifier.isAbstract(method.modifiers) }
            .mapNotNull { it.toMethodRef(thisKlassDesc, KlassDesc(it.returnType)) }
            .toSet()

        // 2. Métodos implementados no DSL
        val implementedMethods = methodRefs.toSet()

        // 3. Comparação (por assinatura)
        val missing = required.filter { required ->
            implementedMethods.none { impl ->
                impl.name == required.name && impl.methodTypeDesc == required.methodTypeDesc
            }
        }

        // 4. Erro se faltar algo
        if (missing.isNotEmpty()) {
            error(buildString {
                appendLine("Missing implementations:")
                missing.forEach {
                    appendLine(" - ${it.name} ${it.methodTypeDesc}")
                }
            })
        }
    }

    private fun build(): ByteArray {
        checkMethodImplementations()

        // Add the required no-argument constructor if the class has no explicit constructors
        if (!hasNoArgsConstructor) constructor()

        return ClassFile.of().build(ClassDesc.of(name)) { clb ->
            val flags = flagsScope.build()
            if (flags and ACC_PUBLIC == 0) {
                println("[WARNING]: class $name is not public. Consider setting access flags with 'access'.")
            }

            clb.withFlags(flagsScope.build())

            val thisKClass = thisKlassDesc.kClass

            val desc = thisKlassDesc.kClass.java.describeConstable().getOrNull()
                ?: throw IllegalStateException("ClassDesc for $name is not available. Ensure the class is properly defined.")

            if (thisKClass.java.isInterface) clb.withInterfaces(Interfaces.ofSymbols(desc).interfaces())
            else if (thisKClass.isAbstract) clb.withSuperclass(desc)
            else throw BadInheritError(thisClassDesc.displayName(), desc.displayName())

            fieldScope.build(clb)
            methodRefs.forEach { m ->
                val fn = m.code ?: return@forEach
                clb.withMethod(m.name, m.methodTypeDesc, m.flags) { mb ->
                    mb.withCode { cb -> fn(cb) }
                }
            }
        }
    }

    companion object {
        /**
         * Creates a [Klass] by applying the provided DSL block to a new [KlassFileBuilder].
         *
         * This is the recommended entry point when constructing a generated class from scratch.
         *
         * @param name the binary name of the class to generate.
         * @param builder the DSL block used to configure the builder.
         * @return the generated class wrapper.
         */
        fun <T : Any> klass(
            name: String,
            inheritor: KClass<T>,
            builder: KlassFileBuilder<T>.() -> Unit,
        ): KlassFileBuilder<T>.Klass = KlassFileBuilder(name, inheritor).also(builder).klass()

        inline fun <reified T : Any> klass(
            name: String,
            noinline builder: KlassFileBuilder<T>.() -> Unit,
        ): KlassFileBuilder<T>.Klass = klass(name, T::class, builder)
    }
}
