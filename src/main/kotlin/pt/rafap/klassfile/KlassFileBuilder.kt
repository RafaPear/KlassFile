package pt.rafap.klassfile

import pt.rafap.klassfile.builders.FieldScope
import pt.rafap.klassfile.builders.FlagsScope
import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.models.FieldRef.Companion.toFieldRefEntry
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.utils.*
import java.lang.classfile.*
import java.lang.classfile.ClassFile.*
import java.lang.classfile.constantpool.InterfaceMethodRefEntry
import java.lang.classfile.constantpool.MethodRefEntry
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.*
import java.lang.constant.MethodTypeDesc
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions


/**
 * DSL builder for defining a class structure, its members, and its bytecode-level behavior.
 *
 * The builder collects fields, methods, implemented interfaces, and superclass information, then emits a generated
 * class representation through the `klass()` API.
 */
@KlassFileDsl
@Suppress("unused")
class KlassFileBuilder private constructor(val name: String, builder: KlassFileBuilder.() -> Unit = {}) {

    val thisClassDesc = classDesc(name)

    private val flagsScope = FlagsScope.ClassFlagsScope(name)
    private val fieldScope = FieldScope(thisClassDesc)
    private val methods = mutableListOf<ClassBuilder.() -> Unit>()
    private val methodRefs = mutableListOf<MethodRef>()
    private val interfaces = mutableListOf<KlassDesc<*>>()
    private var superclass: KlassDesc<*> = KlassDesc.withType<Any>() // Defaults to Object
    private var hasNoArgsConstructor: Boolean = false


    init {
        builder()
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
        type: ClassDesc = CD_void,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<T>> = fieldScope.field(type, access)

    inline fun <reified T : Any> field(
        noinline access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ) = field<T>(classDesc<T>(), access)

    inline fun <reified T> method(
        flags: Int,
        vararg params: KClass<*>,
        noinline body: CodeBuilder.() -> Unit,
    ): EagerDelegate<MethodRef> {

        val params = params.map { classDesc(it) }

        return EagerDelegate { _, property ->
            val name = property.name

            method(
                name,
                flags,
                classDesc<T>(),
                *params.toTypedArray(),
                body = body
            )
        }
    }

    /**
     * Creates a [MethodTypeDesc] from a return type and a list of argument types.
     *
     * This is a thin wrapper around [MethodTypeDesc.of].
     *
     * @param returnType the method return type. Defaults to [CD_void].
     * @param argTypes the method argument types, in declaration order.
     * @return the corresponding [MethodTypeDesc].
     */
    fun methodDesc(
        returnType: ClassDesc? = null,
        vararg argTypes: ClassDesc,
    ): MethodTypeDesc {
        val newReturnType = returnType ?: CD_void
        return MethodTypeDesc.of(newReturnType, *argTypes)
    }

    /**
     * Registers an interface to be implemented by the generated class.
     *
     * Interfaces are emitted in the order they are added.
     *
     * @param classDesc the interface descriptor to add.
     */
    fun <T : Any> implements(classDesc: ClassDesc, kClass: KClass<T>? = null) {
        interfaces.add(KlassDesc(classDesc, kClass))
    }

    /**
     * Registers an interface to be implemented by the generated class, inferring its descriptor from [T].
     *
     * @param T the interface type.
     */
    inline fun <reified T : Any> implements() = implements(classDesc<T>(), T::class)

    /**
     * Sets the superclass of the generated class.
     *
     * If not explicitly set, the superclass defaults to [CD_Object].
     *
     * @param classDesc the superclass descriptor.
     */
    fun extends(classDesc: ClassDesc = CD_Object, kClass: KClass<*>? = null) {
        superclass = KlassDesc(classDesc, kClass)
    }

    /**
     * Sets the superclass of the generated class, inferring the descriptor from [T].
     *
     * @param T the superclass type.
     */
    inline fun <reified T> extends() = extends(classDesc<T>(), T::class)

    /**
     * Either implements [T] or extends it, depending on what kind of type [T] is.
     *
     * Interfaces are added to the implemented-interface list; abstract classes are used as the superclass; concrete
     * classes are rejected.
     *
     * @param T the type to inherit from.
     * @throws IllegalArgumentException if [T] is neither an interface nor an abstract class.
     */
    inline fun <reified T : Any> inherit() {
        val clazz = T::class
        if (clazz.java.isInterface) implements<T>()
        else if (clazz.isAbstract) extends<T>()
        else throw BadInheritError(thisClassDesc.displayName(), classDesc<T>().displayName())
    }

    /////// CODE BUILDER ///////

    /**
     * Loads a constant value onto the operand stack via the constant pool.
     *
     * Supported values are [String], [Int], [Long], [Float], and [Double]. Any other type is rejected.
     *
     * @param T the constant type.
     * @param constant the constant value to load.
     * @throws IllegalArgumentException if the type of [constant] is not supported.
     */
    inline fun <reified T> CodeBuilder.ldc(constant: T? = null) {
        when (constant) {
            is String -> ldc(constantPool().stringEntry(constant))
            is Int -> ldc(constantPool().intEntry(constant))
            is Long -> ldc(constantPool().longEntry(constant))
            is Float -> ldc(constantPool().floatEntry(constant))
            is Double -> ldc(constantPool().doubleEntry(constant))
            else -> ldc(constantPool().classEntry(classDesc<T>()))
        }
    }

    fun CodeBuilder.box(desc: ClassDesc): ClassDesc {
        return when (desc) {

            CD_int -> invokestatic(
                ClassDesc.of("java.lang.Integer"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Integer"),
                    CD_int
                )
            ).let { CD_Integer }

            CD_boolean -> invokestatic(
                ClassDesc.of("java.lang.Boolean"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Boolean"),
                    CD_boolean
                )
            ).let { CD_Boolean }

            CD_long -> invokestatic(
                ClassDesc.of("java.lang.Long"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Long"),
                    CD_long
                )
            ).let { CD_Long }

            CD_double -> invokestatic(
                ClassDesc.of("java.lang.Double"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Double"),
                    CD_double
                )
            ).let { CD_Double }

            CD_float -> invokestatic(
                ClassDesc.of("java.lang.Float"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Float"),
                    CD_float
                )
            ).let { CD_Float }

            CD_short -> invokestatic(
                ClassDesc.of("java.lang.Short"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Short"),
                    CD_short
                )
            ).let { CD_Short }

            CD_byte -> invokestatic(
                ClassDesc.of("java.lang.Byte"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Byte"),
                    CD_byte
                )
            ).let { CD_Byte }

            CD_char -> invokestatic(
                ClassDesc.of("java.lang.Character"),
                "valueOf",
                MethodTypeDesc.of(
                    ClassDesc.of("java.lang.Character"),
                    CD_char
                )
            ).let { CD_Character }

            else -> {
                desc
            }
        }
    }

    private fun resolveMultipleMethods(
        name: String,
        kclass: KClass<*>,
    ): List<MethodRef> =
        kclass.java.methods
            .filter { it.name == name }
            .map { it.toMethodRef() }

    /**
     * Resolves a public method from the given class and converts it into a [MethodRef].
     *
     * This utility performs overload resolution in multiple stages:
     *
     * 1. Resolves all public methods with the given [name].
     * 2. If only one method exists, it is immediately returned.
     * 3. If [returnType] is provided, methods are filtered by return type.
     * 4. If [args] are provided, methods are filtered by parameter types.
     * 5. If a single method is resolved at any stage, it is returned.
     * 6. If multiple candidates remain, an ambiguity error is thrown.
     *
     * Only public methods visible through Java reflection (`kclass.java.methods`)
     * are considered.
     *
     * Example:
     * ```kotlin
     * resolveMethod(
     *     name = "println",
     *     kclass = PrintStream::class,
     *     returnType = CD_void,
     *     args = ClassDesc.of("java.lang.String")
     * )
     * ```
     *
     * @param name
     * Name of the method to resolve.
     *
     * @param kclass
     * Target class where the method will be searched.
     *
     * @param returnType
     * Optional expected return type used to disambiguate overloaded methods.
     *
     * @param args
     * Optional parameter types used for overload resolution.
     *
     * @return
     * A resolved [MethodRef] representing the target method.
     *
     * @throws NoSuchMethodException
     * If no public method with the given name exists in the class.
     *
     * @throws IllegalStateException
     * If multiple overloads still match after applying all resolution filters.
     */
    fun resolveMethod(
        name: String,
        kclass: KClass<*>,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): MethodRef {

        val thisRefInfo = MethodRef(
            owner = classDesc(kclass),
            name = name,
            methodTypeDesc = MethodTypeDesc.of(
                returnType ?: CD_void,
                *args
            ),
            flags = 0,
            isConstructor = false
        )

        // First, try to find the method by the name

        val crudeKotlinMethods = kclass.declaredFunctions.map {
            val parameters = it.parameters.map { param ->
                classDesc(param.type)
            }.toTypedArray()

            val returnType = classDesc(it.returnType)

            MethodRef(
                classDesc(kclass),
                it.name,
                methodDesc(returnType, *parameters),
                0,
                false
            )
        }

        val crudeJavaMethods = kclass.java.methods.map {
            val parameters = it.parameters.map { param ->
                param.type.describeConstable().get()
            }.toTypedArray()

            val returnType = it.returnType.describeConstable().get()

            MethodRef(
                classDesc(kclass),
                it.name,
                methodDesc(returnType, *parameters),
                it.modifiers,
                false
            )
        }

        val crudeKotlinConstructors = kclass.constructors.map {
            val parameters = it.parameters.map { param ->
                classDesc(param.type)
            }

            MethodRef(
                classDesc(kclass),
                INIT_NAME,
                methodDesc(CD_void, *parameters.toTypedArray()),
                0,
                true
            )
        }

        val crudeJavaConstructors = kclass.java.declaredConstructors.map {
            val parameters = it.parameters.map { param ->
                param.type.describeConstable().get()
            }

            MethodRef(
                classDesc(kclass),
                INIT_NAME,
                methodDesc(CD_void, *parameters.toTypedArray()),
                it.modifiers,
                true
            )
        }.filterNot {
            it.methodTypeDesc
                .parameterList()
                .map { p -> p.displayName() }
                .contains("DefaultConstructorMarker")
        }

        val crudeMethods = (crudeJavaMethods + crudeKotlinMethods).toSet()
        val crudeConstructors = (crudeJavaConstructors + crudeKotlinConstructors).toSet()

        var methods = (crudeMethods + crudeConstructors).filter { it.name == name }

        if (methods.isEmpty()) {
            throw NoSuchMethodException(
                buildString {
                    appendLine("COULD NOT RESOLVE PUBLIC METHOD FOR:")
                    appendLine("  $thisRefInfo")

                    appendLine("\nAVAILABLE CONSTRUCTORS:")
                    crudeConstructors.forEach {
                        appendLine("  $it")
                    }

                    appendLine("\nAVAILABLE METHODS:")
                    crudeMethods.forEach {
                        appendLine("  $it")
                    }
                }
            )
        }

        // If only one method is present then the method is resolved
        // as it has no overloads.
        if (methods.size == 1) {
            return methods.first()
        }

        // Resolve by the return type if not resolved already
        if (returnType != null) {
            val filtered =
                methods.filter {
                    val methodReturnType = it.methodTypeDesc.returnType()
                    methodReturnType == returnType
                }

            if (filtered.size == 1)
                return filtered.first()

            if (filtered.isNotEmpty())
                methods = filtered
        }

        // Finaly, resolve by the parameters if not resolved already

        val filtered =
            methods.filter { method ->

                val params = method.methodTypeDesc.parameterArray().toList()

                params == args.toList()
            }

        if (filtered.size == 1)
            return filtered.first()

        if (filtered.isNotEmpty())
            methods = filtered


        // If the method is still ambiguous, throw an error with all the candidates
        // This will happen when the caller of this function does not specify any
        // overload resolution information and the class has multiple methods with
        // the same name.
        error(
            buildString {
                appendLine("AMBIGUOUS METHOD RESOLUTION FOR")
                appendLine("  $thisRefInfo")
                appendLine()
                appendLine("CANDIDATE METHODS:")

                methods.forEach {
                    appendLine("  $it")
                }
            }
        )
    }

    inline fun <reified T> resolveMethod(
        name: String,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): MethodRef =
        resolveMethod(name, T::class, returnType, *args)

    /**
     * Builds a [MethodRefEntry] for a public method on [T] by resolving it through reflection.
     *
     * The method is looked up with [Class.getMethod], so only public methods are resolved here.
     *
     * @param T the class that declares the method, as a reified type parameter.
     * @param name the method name.
     * @param args the method parameter types used to identify the overload.
     * @return a [MethodRefEntry] describing the resolved method.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     */
    inline fun <reified T : Any> CodeBuilder.buildMethodRefEntry(
        name: String,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): MethodRefEntry {
        return buildMethodRefEntry(name, T::class, returnType, *args)
    }

    fun CodeBuilder.buildMethodRefEntry(
        name: String,
        kclass: KClass<*>,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): MethodRefEntry {
        val ref = resolveMethod(name, kclass, returnType, *args)
        return constantPool().methodRefEntry(classDesc(kclass), ref.name, ref.methodTypeDesc)
    }

    /**
     * Builds a [InterfaceMethodRefEntry] for a public method on [T] by resolving it through reflection.
     *
     * The method is looked up with [Class.getMethod], so only public methods are resolved here.
     *
     * @param T the class that declares the method, as a reified type parameter.
     * @param name the method name.
     * @param args the method parameter types used to identify the overload.
     * @return a [MethodRefEntry] describing the resolved method.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     */
    inline fun <reified T> CodeBuilder.buildInterfaceMethodRefEntry(
        name: String,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): InterfaceMethodRefEntry {
        val ref = resolveMethod<T>(name, returnType, *args)
        return constantPool().interfaceMethodRefEntry(classDesc<T>(), ref.name, ref.methodTypeDesc)
    }

    /**
     * Invokes a public virtual method on [T] by resolving it through reflection.
     *
     * This overload is convenient when the target method is known by name and parameter types.
     *
     * @param T the declaring class of the method.
     * @param name the method name.
     * @param args the argument types used to resolve the overload.
     * @return this [CodeBuilder] for chaining.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     */
    inline fun <reified T> CodeBuilder.invokevirtual(
        name: String,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): CodeBuilder = invokevirtual(resolveMethod<T>(name, returnType, *args))

    /**
     * Invokes a public interface method on [T] by resolving it through reflection.
     *
     * This overload is convenient when the target method is known by name and parameter types,
     * and it emits an `invokeinterface` instruction instead of `invokevirtual`.
     *
     * @param T the declaring interface of the method.
     * @param name the method name.
     * @param args the argument types used to resolve the overload.
     * @return this [CodeBuilder] for chaining.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     */
    inline fun <reified T> CodeBuilder.invokeinterface(
        name: String,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): CodeBuilder =
        invokeinterface(buildInterfaceMethodRefEntry<T>(name, returnType, *args))

    /**
     * Invokes a virtual method using a prebuilt [MethodRef].
     *
     * This is the preferred overload when the target method is already described in the generated class or when the
     * caller already has a fully populated method reference.
     *
     * @param methodRef the method reference to invoke.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.invokevirtual(methodRef: MethodRef): CodeBuilder {
        return invokevirtual(methodRef.owner, methodRef.name, methodRef.methodTypeDesc)
    }

    /**
     * Invokes a public static method on [T] by resolving it through reflection.
     *
     * This uses [Class.getMethod], so the method must be public.
     *
     * @param T the declaring class of the method.
     * @param name the method name.
     * @param args the method parameter types used to resolve the overload.
     * @return this [CodeBuilder] for chaining.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     */
    inline fun <reified T : Any> CodeBuilder.invokestatic(
        name: String,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ): CodeBuilder =
        invokestatic(buildMethodRefEntry<T>(name, returnType, *args))

    /**
     * Invokes a static method using a prebuilt [MethodRef].
     *
     * This avoids reflection and directly emits the invocation described by [methodRef].
     *
     * @param methodRef the method reference to invoke.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.invokestatic(methodRef: MethodRef): CodeBuilder =
        invokestatic(methodRef.owner, methodRef.name, methodRef.methodTypeDesc)

    /**
     * Invokes a constructor or another special method on [T] by resolving it through reflection.
     * When it is a constructor, the generated bytecode sequence includes object instantiation and duplication
     * of the reference for the constructor call.
     *
     * When [name] is `INIT_NAME`, a matching constructor is resolved with [Class.getConstructor]. Otherwise, a public
     * method is resolved with [Class.getMethod] and emitted as a special invocation.
     *
     * @param T the declaring class of the member.
     * @param name the member name. Defaults to [INIT_NAME].
     * @param args the parameter types used to resolve the overload.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     * @return this [CodeBuilder] for chaining.
     */
    inline fun <reified T : Any> CodeBuilder.invokespecial(
        name: String = INIT_NAME,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ) {
        invokespecial(T::class, name, returnType, *args)
    }

    fun <T : Any> CodeBuilder.invokespecial(
        owner: KClass<T>,
        name: String = INIT_NAME,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ) {
        val isAbstractClass = !owner.java.isInterface && owner.isAbstract
        if (name == INIT_NAME && !isAbstractClass) {
            val method = resolveMethod(INIT_NAME, owner, returnType, *args)
            invokespecial(method)
        } else if (isAbstractClass) {
            val method = resolveMethod(INIT_NAME, owner, returnType, *args)
            invokespecial(method)
        } else invokespecial(buildMethodRefEntry(name, owner, returnType, *args))
    }

    inline fun <reified T : Any> CodeBuilder.instanciate(
        name: String = INIT_NAME,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ) {
        new_(classDesc<T>())
        dup()
        invokespecial<T>(name, returnType, *args)
    }

    inline fun <reified T : Any> CodeBuilder.instanciateField(
        fieldRef: FieldRef<T>,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
    ) {
        aload(0)
        instanciate<T>(returnType = returnType, args = args)
        putfield(fieldRef)
    }

    /**
     * Invokes a constructor or private method using a prebuilt [MethodRef].
     *
     * This overload is intended for references created while building the same class, including constructors and
     * private methods that should not be resolved through reflection.
     *
     * @param methodRef the constructor or special-method reference to invoke.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.invokespecial(methodRef: MethodRef): CodeBuilder =
        invokespecial(methodRef.owner, methodRef.name, methodRef.methodTypeDesc)

    /**
     * Invokes a method using a [MethodRef] and chooses the opcode from the reference metadata.
     *
     * Static members use [invokestatic], constructors and private methods use [invokespecial], and all remaining
     * members use [invokevirtual]. When the reference targets the class currently being built, `this` is loaded before
     * dispatching the invocation.
     *
     * @param methodRef the method reference to invoke.
     */
    fun CodeBuilder.invokeMethod(methodRef: MethodRef) {
        when {
            methodRef.isStatic -> invokestatic(methodRef)
            methodRef.isConstructor -> invokespecial(methodRef)
            else -> invokevirtual(methodRef)
        }
    }

    context(cb: CodeBuilder)
    operator fun MethodRef.invoke() {
        cb.invokeMethod(this)
    }

    /**
     * Reads a public field from [T] by resolving it through reflection and emitting [getstatic].
     *
     * This overload uses [Class.getField], so the field must be public.
     *
     * @param T the declaring class of the field.
     * @param name the field name.
     * @throws NoSuchFieldException if no public field with the given name exists in [T].
     */
    inline fun <reified T> CodeBuilder.getstatic(name: String) {
        val field = T::class.java.getField(name)

        getstatic(classDesc(field.declaringClass), field.name, classDesc(field.type))
    }

    /**
     * Reads a field using a [FieldRef].
     *
     * This overload avoids reflection and emits a direct field-read instruction from the supplied reference.
     *
     * @param ref the field reference to read.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.getfield(ref: FieldRef<*>): CodeBuilder =
        if (ref.isStatic) getstatic(ref)
        else getfield(toFieldRefEntry(ref))

    /**
     * Reads an instance field from the current class using a [FieldRef].
     *
     * The emitted bytecode loads `this` first and then reads the referenced field.
     *
     * @param ref the instance field reference to read.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.getThisField(ref: FieldRef<*>): CodeBuilder {
        aload(0)
        return getfield(ref)
    }

    /**
     * Reads a static field using a [FieldRef].
     *
     * @param ref the static field reference to read.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.getstatic(ref: FieldRef<*>): CodeBuilder = getstatic(toFieldRefEntry(ref))

    /**
     * Writes the stack top into a field referenced by a [FieldRef].
     *
     * The caller is responsible for ensuring that the receiver object and value are already on the operand stack in
     * the order expected by `putfield`.
     *
     * @param ref the field reference to write.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.putfield(ref: FieldRef<*>): CodeBuilder =
        if (ref.isStatic) putstatic(ref)
        else putfield(toFieldRefEntry(ref))

    fun CodeBuilder.putThisField(ref: FieldRef<*>, const: Any): CodeBuilder {
        aload(0)
        ldc(const)
        return putfield(ref)
    }

    /**
     * Writes the stack top into a static field referenced by a [FieldRef].
     *
     * @param ref the static field reference to write.
     * @return this [CodeBuilder] for chaining.
     */
    fun CodeBuilder.putstatic(ref: FieldRef<*>): CodeBuilder = putstatic(toFieldRefEntry(ref))

    /**
     * Emits the bytecode sequence for a direct superclass constructor call.
     *
     * The generated sequence is equivalent to `aload_0` followed by `invokespecial super.<init>()`.
     */
    fun CodeBuilder.defaultConstructor() {
        aload(0)
        invokespecial(superclass.classDesc, INIT_NAME, MTD_void)
    }

    /**
     * Builds a simple `while` loop structure.
     *
     * The [condition] block receives the loop-exit label and is expected to jump to it when the loop should stop.
     * The [body] block is emitted between the condition and the backward jump to the loop start.
     *
     * @param condition code that evaluates the loop condition and receives the exit label.
     * @param body the loop body.
     */
    fun CodeBuilder.whileLoop(
        condition: CodeBuilder.(Label) -> Unit,
        body: CodeBuilder.() -> Unit,
    ) {
        val start = newLabel()
        val end = newLabel()

        labelBinding(start)
        condition(end)
        body()
        goto_(start)
        labelBinding(end)
    }

    /////// DSL ///////

    /**
     * Declares a constructor for the generated class.
     *
     * If [flags] includes [ACC_STATIC], the generated member is named [CLASS_INIT_NAME] instead of [INIT_NAME],
     * effectively creating a class initializer.
     *
     * @param flags the constructor access flags. Defaults to [ACC_PUBLIC].
     * @param args the constructor parameter types.
     * @param body the constructor body. By default, it invokes the superclass constructor and returns.
     * @return a [MethodRef] describing the generated member.
     */
    fun constructor(
        flags: Int = ACC_PUBLIC,
        vararg args: ClassDesc,
        body: CodeBuilder.() -> Unit = { defaultConstructor(); return_() },
    ): MethodRef {
        val name = if (flags and ACC_STATIC == ACC_STATIC) CLASS_INIT_NAME else INIT_NAME

        hasNoArgsConstructor = hasNoArgsConstructor || args.isEmpty()

        return method(name, flags, returnType = CD_void, args = args, body)
    }

    fun method(methodRef: MethodRef, body: CodeBuilder.() -> Unit) = method(
        methodRef.name,
        methodRef.flags,
        methodRef.methodTypeDesc.returnType(),
        *methodRef.methodTypeDesc.parameterArray(),
        body = body
    )


    /**
     * Declares a method for the generated class and queues it for emission.
     *
     * The returned [MethodRef] can be reused later when generating bytecode that calls the method.
     *
     * @param name the method name.
     * @param flags the method access flags. Defaults to [ACC_PUBLIC].
     * @param returnType the return type of the method. Defaults to [CD_void].
     * @param args the parameter types of the method.
     * @param body the method body.
     * @return a [MethodRef] describing the generated method.
     */
    fun method(
        name: String,
        flags: Int = ACC_PUBLIC,
        returnType: ClassDesc = CD_void,
        vararg args: ClassDesc,
        body: CodeBuilder.() -> Unit,
    ): MethodRef {
        val desc = MethodTypeDesc.of(returnType, *args)

        val ref = MethodRef(
            owner = thisClassDesc,
            name = name,
            methodTypeDesc = desc,
            flags = flags,
            isConstructor = name == INIT_NAME || name == CLASS_INIT_NAME,
        )

        if (!methodRefs.contains(ref)) methodRefs.add(ref)
        else error("Method with name $name and descriptor $desc already exists in class ${thisClassDesc}")

        methods += {
            withMethod(name, desc, flags) { mb ->
                mb.withCode { cob ->
                    cob.body()
                }
            }
        }



        return ref
    }

    /**
     * Declares a public static method for the generated class.
     *
     * This is a convenience wrapper around [method] that applies [ACC_PUBLIC] and [ACC_STATIC].
     *
     * @param name the method name.
     * @param returnType the return type of the method. Defaults to [CD_void].
     * @param params the parameter types of the method.
     * @param body the method body.
     * @return a [MethodRef] describing the generated method.
     */
    fun staticMethod(
        name: String,
        returnType: ClassDesc = CD_void,
        vararg params: ClassDesc,
        body: CodeBuilder.() -> Unit,
    ) = method(name, ACC_PUBLIC or ACC_STATIC, returnType, *params, body = body)

    /**
     * Declares a private method for the generated class.
     *
     * This is a convenience wrapper around [method] that applies [ACC_PRIVATE].
     *
     * @param name the method name.
     * @param returnType the return type of the method. Defaults to [CD_void].
     * @param params the parameter types of the method.
     * @param body the method body.
     * @return a [MethodRef] describing the generated method.
     */
    fun privateMethod(
        name: String,
        returnType: ClassDesc = CD_void,
        vararg params: ClassDesc,
        body: CodeBuilder.() -> Unit,
    ) = method(name, ACC_PRIVATE, returnType, *params, body = body)

    /**
     * Declares the `public static void main(String[])` entry point for the generated class.
     *
     * The parameter list is fixed to a single `String[]` argument and the method is always public and static.
     *
     * @param body the main method body.
     */
    fun main(body: CodeBuilder.() -> Unit) {
        method(
            "main", ACC_PUBLIC or ACC_STATIC, CD_void, CD_String.arrayType()
        ) {
            body()
        }
    }

    fun implementMethod(
        name: String,
        flags: Int = ACC_PUBLIC,
        returnType: ClassDesc? = null,
        vararg args: ClassDesc,
        body: CodeBuilder.() -> Unit,
    ) {

        val inheritedTypes =
            (interfaces.mapNotNull { it.kClass } + listOfNotNull(superclass.kClass))

        val candidates = inheritedTypes
            .flatMap { kclass ->
                resolveMultipleMethods(name, kclass)
            }

        val filtered = candidates.filter { method ->

            val desc = method.methodTypeDesc

            val matchesArgs =
                if (args.isEmpty()) true
                else desc.parameterCount() == args.size &&
                        desc.parameterList() == args.toList()

            val matchesReturn =
                returnType == null ||
                        desc.returnType() == returnType

            matchesArgs && matchesReturn
        }

        if (filtered.isEmpty()) {
            error(
                buildString {
                    appendLine("No matching inherited method found:")
                    appendLine("  name: $name")
                    appendLine("  args: ${args.joinToString()}")
                    appendLine("  returnType: $returnType")
                }
            )
        }

        if (filtered.size > 1) {
            error(
                buildString {
                    appendLine("Ambiguous inherited method:")
                    appendLine("  name: $name")
                    filtered.forEach {
                        appendLine("   - ${it.methodTypeDesc}")
                    }
                }
            )
        }

        val resolved = filtered.first()

        val finalReturnType =
            returnType ?: resolved.methodTypeDesc.returnType()

        val finalArgs =
            if (args.isEmpty())
                resolved.methodTypeDesc.parameterArray()
            else
                args

        method(
            name = name,
            flags = flags,
            returnType = finalReturnType,
            args = finalArgs,
            body = body
        )
    }

    inline fun <reified T : Any> CodeBuilder.autoReturn() {
        val returnType = classDesc<T>()
        when (returnType) {
            CD_void -> return_()
            CD_int -> ireturn()
            CD_long -> lreturn()
            CD_float -> freturn()
            CD_double -> dreturn()
            else -> areturn()
        }
    }

    inline fun <reified T : Any> CodeBuilder.autoLoad(slot: Int) {
        val returnType = classDesc<T>()
        when (returnType) {
            CD_int -> iload(slot)
            CD_long -> lload(slot)
            CD_float -> fload(slot)
            CD_double -> dload(slot)
            else -> aload(slot)
        }
    }

    inline fun <reified T : Any> CodeBuilder.autoStore(slot: Int) {
        val returnType = classDesc<T>()
        when (returnType) {
            CD_int -> istore(slot)
            CD_long -> lstore(slot)
            CD_float -> fstore(slot)
            CD_double -> dstore(slot)
            else -> astore(slot)
        }
    }

    fun FieldRef<*>.genGetterName(): String {
        return "get" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun FieldRef<*>.genSetterName(): String {
        return "set" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    inline fun <reified T : Any> FieldRef<T>.getter(flags: Int = ACC_PUBLIC) =
        method(genGetterName(), flags, classDesc<T>()) {
            getThisField(this@getter)
            autoReturn<T>()
        }

    inline fun <reified T : Any> getter(ref: FieldRef<T>, flags: Int = ACC_PUBLIC) = method<T>(flags) {
        getThisField(ref)
        autoReturn<T>()
    }

    inline fun <reified T : Any> setter(ref: FieldRef<T>, flags: Int = ACC_PUBLIC) = method<Unit>(flags, T::class) {
        aload(0)
        autoLoad<T>(1)
        putfield(ref)
        return_()
    }

    inline fun <reified T : Any> FieldRef<T>.setter(flags: Int = ACC_PUBLIC) =
        method(genSetterName(), flags, CD_void, classDesc<T>()) {
            aload(0)
            autoLoad<T>(1)
            putfield(this@setter)
            return_()
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
        inline fun <reified T : Any> writeAndGetClass(): KClass<T> = writeAndGetClass(name, bytes)

        /**
         * Writes the generated bytes to disk and creates a new instance of the generated class.
         *
         * This requires the generated class to expose a public no-argument constructor compatible with
         * [createInstance].
         *
         * @param T the expected type of the generated instance.
         * @return a new instance of the generated class.
         */
        inline fun <reified T : Any> writeAndGetInstance(): T {
            val kClass = writeAndGetClass<T>()

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

    private fun Method.toMethodRef(): MethodRef =
        MethodRef(
            owner = classDesc(declaringClass),
            name = name,
            methodTypeDesc = methodDesc(
                classDesc(returnType),
                *parameterTypes.map(::classDesc).toTypedArray()
            ),
            flags = modifiers,
            isConstructor = false
        )


    private fun checkMethodImplementations() {
        // 1. Métodos obrigatórios (interfaces + superclasse)
        val requiredMethods = (interfaces + superclass).mapNotNull { it.kClass?.java }.flatMap { clazz ->
            clazz.methods.filter { method ->
                Modifier.isAbstract(method.modifiers)
            }
        }.map { it.toMethodRef() }.toSet()

        // 2. Métodos implementados no DSL
        val implementedMethods = methodRefs.toSet()

        // 3. Comparação (por assinatura)
        val missing = requiredMethods.filter { required ->
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

        val interfaceCD = interfaces.map { it.classDesc }.toTypedArray()

        // Add the required no-argument constructor if the class has no explicit constructors
        if (!hasNoArgsConstructor) constructor()

        return ClassFile.of().build(ClassDesc.of(name)) { clb ->
            val flags = flagsScope.build()
            if (flags and ACC_PUBLIC == 0) {
                println("[WARNING]: class $name is not public. Consider setting access flags with 'access'.")
            }

            clb.withFlags(flagsScope.build())
            clb.withSuperclass(superclass.classDesc)
            clb.withInterfaces(
                Interfaces.ofSymbols(*interfaceCD).interfaces()
            )
            fieldScope.build(clb)
            methods.forEach { clb.it() }

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
        fun klass(name: String, builder: @Suppress("UNUSED") KlassFileBuilder.() -> Unit): Klass =
            KlassFileBuilder(name).also(builder).klass()
    }
}
