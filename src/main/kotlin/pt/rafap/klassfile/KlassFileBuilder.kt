package pt.rafap.klassfile

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
import java.lang.classfile.Interfaces
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.INIT_NAME
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

    /** JVM descriptor for the generated class name. */
    val thisClassDesc = classDesc(name)
    /** Kotlin/JVM descriptor for the class being generated. */
    val thisKlassDesc = KlassDesc(ClassDesc.of(name), inheritor)

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
     *
     * @param type the field type descriptor.
     * @param access a lambda to configure the field's access flags using [FlagsScope.FieldFlagsScope]. Defaults to `private`.
     */
    fun <T : Any> field(
        type: KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<O, T>> = fieldScope.field(type, access)

    /** Adds a delegated field using a reified Kotlin type. */
    inline fun <reified T : Any> field(
        noinline access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ) = field(klassDescOf<T>(), access)

    /** Adds a method with an explicit name and type descriptor. */
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

    /** Adds a method with an explicit name and a reified return type. */
    inline fun <reified R : Any> method(
        name: String,
        invokeType: InvokeType = InvokeType.VIRTUAL,
        noinline builder: MethodScope<O, R>.() -> Unit,
    ) = method(name, klassDescOf<R>(), invokeType, builder)

    /** Adds a delegated method whose name is inferred from the backing property. */
    fun <R : Any> method(
        type: KlassDesc<R>,
        invokeType: InvokeType = InvokeType.VIRTUAL,
        builder: MethodScope<O, R>.() -> Unit,
    ) = EagerDelegate { _, property ->
        method(property.name, type, invokeType, builder)
    }

    /** Adds a delegated method using a reified return type. */
    inline fun <reified R : Any> method(
        invokeType: InvokeType = InvokeType.VIRTUAL,
        noinline builder: MethodScope<O, R>.() -> Unit,
    ) = method(klassDescOf<R>(), invokeType, builder)

    /**
     * Declares a constructor for the generated class.
     *
     * When no body is provided, the generated constructor simply invokes the
     * default superclass constructor and returns.
     *
     * @param builder the constructor body.
     * @return a [MethodRef] describing the generated member.
     */
    fun constructor(
        builder: MethodScope<O, Unit>.() -> Unit = { access { public() }; code { defaultCtor(); ret() } },
    ): MethodRef<O, Unit> {
        hasNoArgsConstructor = true
        return method(INIT_NAME, klassDescOf<Unit>(), InvokeType.SPECIAL, builder = builder)
    }

    /** Generates a conventional getter name for a field reference. */
    fun FieldRef<*, *>.genGetterName(): String {
        return "get" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /** Generates a conventional setter name for a field reference. */
    fun FieldRef<*, *>.genSetterName(): String {
        return "set" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /** Creates a getter method that reads the provided field. */
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

    /** Adds a delegated getter whose name is derived from the field name. */
    inline fun <reified T : Any> getter(
        field: FieldRef<O, T>,
        noinline access: FlagsScope.MethodFlagsScope.() -> Unit = { public() },
    ) = EagerDelegate { _, _ ->
        getter(field.genGetterName(), field, access)
    }

    /** Creates a setter method that writes the provided field. */
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

    /** Adds a delegated setter whose name is derived from the field name. */
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
         * @return the loaded [KClass] instance.
         */
        fun writeAndGetClass(): KClass<O> = writeAndGetClass(name, bytes)

        /**
         * Writes the generated bytes to disk and creates a new instance of the generated class.
         *
         * This requires the generated class to expose a public no-argument constructor compatible with
         * [createInstance].
         *
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


    /**
     * Ensures every abstract method from the inherited type is implemented by the DSL.
     *
     * @throws IllegalStateException when one or more required methods are missing.
     */
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

    /**
     * Builds and returns the final class byte array.
     *
     * @return the generated class bytes.
     */
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
            else throw BadInheritError(thisKlassDesc.classDesc.displayName(), desc.displayName())

            fieldScope.build(clb)
            methodRefs.forEach { m ->
                val fn = m.code ?: throw NoCodeBlockDefinedError(m)
                clb.withMethod(m.name, m.methodTypeDesc, m.flags) { mb ->
                    mb.withCode { cb -> fn(cb) }
                }
            }
        }
    }

    companion object {
        /**
         * Creates a generated class by applying the provided DSL block to a new builder.
         *
         * This is the recommended entry point when constructing a generated class from scratch.
         *
         * @param name the binary name of the class to generate.
         * @param inheritor the Kotlin class used to derive the generated type metadata.
         * @param builder the DSL block used to configure the builder.
         * @return the generated class wrapper.
         */
        fun <T : Any> klass(
            name: String,
            inheritor: KClass<T>,
            builder: KlassFileBuilder<T>.() -> Unit,
        ): KlassFileBuilder<T>.Klass = KlassFileBuilder(name, inheritor).also(builder).klass()

        /** Creates a generated class using a reified inheritor type. */
        inline fun <reified T : Any> klass(
            name: String,
            noinline builder: KlassFileBuilder<T>.() -> Unit,
        ): KlassFileBuilder<T>.Klass = klass(name, T::class, builder)
    }
}
