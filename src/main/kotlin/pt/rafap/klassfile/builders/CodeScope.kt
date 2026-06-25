package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.*
import pt.rafap.klassfile.utils.*
import java.lang.classfile.CodeBuilder
import java.lang.constant.ConstantDescs
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

@CodeScopeDsl
class CodeScope<O : Any, R : Any>(
    val scopeName: String,
    override val type: KlassDesc<R>,
    override val owner: KlassDesc<O>,
    val params: List<ParamRef<*>>,
) : TypedRef<O, R> {
    private var instructions = mutableListOf<CodeBuilder.() -> Unit>()
    private var stackTypes = mutableListOf<KClass<*>>()
    private var invocationTrace = mutableListOf<MethodRef<*, *>>()
    private var hasReturn = false

    fun printStackTypes() {
        if (stackTypes.isEmpty()) return

        println("Current Stack Types:")
        stackTypes.forEachIndexed { index, kClass ->
            println("  [${index}]: ${kClass.simpleName ?: "Unknown"}")
        }
    }

    fun printInvocationTrace() {
        if (invocationTrace.isEmpty()) return

        println("Current Invocation Trace:")
        invocationTrace.forEachIndexed { index, methodRef ->
            println("  [${index}]: $methodRef")
        }
    }

    fun printStack() {
        printStackTypes()
        printInvocationTrace()
    }

    private fun pushStack(type: KClass<*>) {
        if (type == Unit::class) return
        stackTypes.add(type)
    }

    private fun pushStack(type: KlassDesc<*>) {
        val kClass = type.kClass
        pushStack(kClass)
    }

    private fun pushStack(ref: ParamRef<*>) {
        val kClass = ref.type.kClass
        pushStack(kClass)
    }

    private fun pushStack(ref: FieldRef<*, *>) {
        val kClass = ref.type.kClass
        pushStack(kClass)
    }

    private fun pushStack(ref: MethodRef<*, *>) {
        val kClass = ref.type.kClass
        pushStack(kClass)
    }

    private inline fun <reified T : Any> pushStack() = pushStack(T::class)
    private fun popStack(): KClass<*> {
        return stackTypes.removeLastOrNull() ?: throw StackUnderflowError(this)
    }

    private fun popStack(expected: KlassDesc<*>): KClass<*> {
        if (expected.classDesc == ConstantDescs.CD_void) error("Cannot pop stack for void type in '${scopeName}'")
        expectTop(expected)
        return stackTypes.removeLast()
    }

    private fun stackInvoke(ref: MethodRef<*, *>) {
        invocationTrace.add(ref)
        for (param in ref.params.asReversed()) {
            popStack(param.type)
        }
        if (ref.invokeType != InvokeType.STATIC) {
            popStack(ref.owner)
            pushStack(ref)
        }
    }

    private fun expectTop(expected: KlassDesc<*>) {
        val kClass = expected.kClass

        val actual = stackTypes.lastOrNull()
            ?: throw StackUnderflowError(this)

        if (!expected.kClass.isSuperclassOf(actual)) {
            throw StackTypeMismatchError(expected.kClass, actual, this)
        }
    }

    private var isInsideRawBlock = false

    private fun raw(block: CodeBuilder.() -> Unit) {
        if (isInsideRawBlock) throw NestedRawBlockError()
        isInsideRawBlock = true
        instructions.add(block)
        isInsideRawBlock = false
    }

    fun <T : Any> new(type: KlassDesc<T>) {
        raw { new_(type.classDesc) }
        pushStack(type)
    }

    fun dup() {
        val topType = stackTypes.lastOrNull() ?: throw StackUnderflowError(this@CodeScope)
        raw { dup() }
        pushStack(topType)
    }

    fun defaultCtor() {
        loadReceiver()
        val ownerKClass = owner.kClass
        if (!ownerKClass.isFinal && !ownerKClass.java.isInterface) {
            val ref = findMethod<O, Unit>(ConstantDescs.INIT_NAME, owner, klassDescOf()) {}
            invokeSpecial(ref)
        } else if (ownerKClass.java.isInterface) {
            val ref = findMethod<Any, Unit>(ConstantDescs.INIT_NAME) {}
            invokeSpecial(ref)
        } else throw NoConstructorError(ownerKClass.simpleName ?: "Unknown")
    }

    fun ret() {
        hasReturn = true
        if (type.classDesc != klassDescOf<Unit>().classDesc)
            popStack(type)

        raw {
            when (type.classDesc) {
                ConstantDescs.CD_void -> return_()
                ConstantDescs.CD_int -> ireturn()
                ConstantDescs.CD_long -> lreturn()
                ConstantDescs.CD_float -> freturn()
                ConstantDescs.CD_double -> dreturn()
                else -> areturn()
            }
        }
    }

    private fun getParam(slot: Int) = params.firstOrNull { it.order == slot }
        ?: throw InvalidSlotIndexError(slot)

    fun load(slot: Int) {
        val param = getParam(slot)
        raw {
            when (param.type.classDesc) {
                ConstantDescs.CD_int -> iload(slot)
                ConstantDescs.CD_long -> lload(slot)
                ConstantDescs.CD_float -> fload(slot)
                ConstantDescs.CD_double -> dload(slot)
                else -> aload(slot)
            }
        }
        pushStack(param)
    }

    fun loadReceiver() {
        val param = params.firstOrNull { it is ParamRef.ReceiverRef<*> }
            ?: throw NoParamFoundError("receiver")
        load(param.order)
    }

    fun store(slot: Int) {
        val param = getParam(slot)
        popStack(param.type)

        raw {
            when (param.type.classDesc) {
                ConstantDescs.CD_int -> istore(slot)
                ConstantDescs.CD_long -> lstore(slot)
                ConstantDescs.CD_float -> fstore(slot)
                ConstantDescs.CD_double -> dstore(slot)
                else -> astore(slot)
            }
        }
    }

    inline fun <reified T : ParamRef<*>> load(ref: T) = load(ref.order)


    inline fun <reified T : ParamRef<*>> store(ref: T) = store(ref.order)

    fun add() {
        val topType = popStack()
        val secondType = popStack()

        if (topType != secondType) return

        raw {
            when (topType) {
                Int::class -> iadd()
                Long::class -> ladd()
                Float::class -> fadd()
                Double::class -> dadd()
                else -> throw UnsupportedOperationException("Add operation not supported for type: $topType")
            }
        }
        pushStack(secondType)
    }

    fun add(a: ParamRef<*>, b: ParamRef<*>) {
        load(a)
        load(b)
        add()
    }

    operator fun <T : Any> ParamRef<T>.plus(ref: ParamRef<T>) = add(this, ref)

    fun <T : Any> ldc(constant: T? = null, kClass: KClass<T>) {
        raw {
            when (constant) {
                is String -> ldc(constantPool().stringEntry(constant))
                is Int -> ldc(constantPool().intEntry(constant))
                is Long -> ldc(constantPool().longEntry(constant))
                is Float -> ldc(constantPool().floatEntry(constant))
                is Double -> ldc(constantPool().doubleEntry(constant))
                else -> ldc(constantPool().classEntry(classDesc(kClass)))
            }
        }

        pushStack(kClass)
    }

    /**
     * Loads a constant value onto the operand stack via the constant pool.
     *
     * Supported values are [String], [Int], [Long], [Float], and [Double]. Any other type is rejected.
     *
     * @param T the constant type.
     * @param constant the constant value to load.
     * @throws IllegalArgumentException if the type of [constant] is not supported.
     */
    inline fun <reified T : Any> ldc(constant: T? = null) = ldc(constant, T::class)

    fun <O : Any, R : Any> findMethod(
        name: String,
        owner: KlassDesc<O>,
        returnType: KlassDesc<R>,
        builder: ParameterScope.() -> Unit,
    ): MethodRef<O, R> {
        val params = ParameterScope().apply(builder).build().toTypedArray()
        return resolveMethod(name, owner, returnType, *params)
    }

    inline fun <reified O : Any, reified R : Any> findMethod(
        name: String,
        noinline builder: ParameterScope.() -> Unit,
    ) = findMethod(name, klassDescOf<O>(), klassDescOf<R>(), builder)

    inline fun <reified O : Any, reified R : Any> findMethod(
        noinline builder: ParameterScope.() -> Unit,
    ) = EagerDelegate { _, property -> findMethod<O, R>(property.name, builder) }

    private fun assertInvokeType(expected: InvokeType, ref: MethodRef<*, *>) {
        if (expected != ref.invokeType)
            throw InvokeReferenceError(expected, ref)
    }

    fun invokeVirtual(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.VIRTUAL, ref)
        stackInvoke(ref)

        raw { invokevirtual(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    inline fun <reified O : Any, reified R : Any> invokeVirtual(
        name: String,
        noinline builder: ParameterScope.() -> Unit,
    ) = invokeVirtual(findMethod<O, R>(name, builder))

    fun invokeInterface(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.INTERFACE, ref)
        stackInvoke(ref)

        raw { invokeinterface(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    inline fun <reified O : Any, reified R : Any> invokeInterface(
        name: String,
        noinline builder: ParameterScope.() -> Unit,
    ) = invokeInterface(findMethod<O, R>(name, builder))

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
    fun invokeStatic(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.STATIC, ref)
        stackInvoke(ref)

        raw { invokestatic(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    inline fun <reified O : Any, reified R : Any> invokeStatic(
        name: String,
        noinline builder: ParameterScope.() -> Unit,
    ) = invokeStatic(findMethod<O, R>(name, builder))


    /**
     * Invokes a constructor or another special method on [T] by resolving it through reflection.
     * When it is a constructor, the generated bytecode sequence includes object instantiation and duplication
     * of the reference for the constructor call.
     *
     * When [scopeName] is `INIT_NAME`, a matching constructor is resolved with [Class.getConstructor]. Otherwise, a public
     * method is resolved with [Class.getMethod] and emitted as a special invocation.
     *
     * @param T the declaring class of the member.
     * @param name the member name. Defaults to [ConstantDescs.INIT_NAME].
     * @param args the parameter types used to resolve the overload.
     * @throws NoSuchMethodException if no method with the given name and argument types is found in class [T].
     * @return this [CodeBuilder] for chaining.
     */
    fun invokeSpecial(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.SPECIAL, ref)
        stackInvoke(ref)

        raw { invokespecial(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    fun <O : Any> instantiate(
        owner: KlassDesc<O>,
        builder: ParameterScope.() -> Unit = {},
    ) {
        new(owner)
        dup()
        val ref = findMethod<O, Unit>(ConstantDescs.INIT_NAME, owner, klassDescOf(), builder)
        invokeSpecial(ref)
    }

    inline fun <reified O : Any> instantiate(
        noinline builder: ParameterScope.() -> Unit = {},
    ) {
        instantiate(klassDescOf<O>(), builder)
    }

    inline fun <reified T : Any> instanciateField(
        fieldRef: FieldRef<*, T>,
        noinline builder: ParameterScope.() -> Unit,
    ) {
        val param = params.getOrNull(0)
            ?: throw NoParamFoundError("receiver for field ${fieldRef.name}")

        load(param)
        instantiate<T>(builder)
        putField(fieldRef)
    }

    /**
     * Invokes a method using a [MethodRef] and chooses the opcode from the reference metadata.
     *
     * Static members use [invokeStatic], constructors and private methods use [invokeSpecial], and all remaining
     * members use [invokevirtual]. When the reference targets the class currently being built, `this` is loaded before
     * dispatching the invocation.
     *
     * @param methodRef the method reference to invoke.
     */
    fun invokeMethod(methodRef: MethodRef<*, *>) {
        when (methodRef.invokeType) {
            InvokeType.STATIC -> invokeStatic(methodRef)
            InvokeType.SPECIAL -> invokeSpecial(methodRef)
            InvokeType.VIRTUAL -> invokeVirtual(methodRef)
            InvokeType.INTERFACE -> invokeInterface(methodRef)
        }
    }

    operator fun MethodRef<*, *>.invoke() {
        invokeMethod(this)
    }

    fun getStatic(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        raw { getstatic(owner.classDesc, name, type.classDesc) }
        pushStack(type)
    }

    inline fun <reified O : Any, reified T : Any> getStatic(name: String) =
        getStatic(name, klassDescOf<O>(), klassDescOf<T>())

    fun getStatic(ref: FieldRef<*, *>) = getStatic(ref.name, ref.owner, ref.type)

    fun getField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        popStack(owner)
        raw { getfield(owner.classDesc, name, type.classDesc) }
        pushStack(type)
    }

    fun getField(ref: FieldRef<*, *>) {
        if (ref.isStatic) getStatic(ref)
        else getField(ref.name, ref.owner, ref.type)
    }

    /**
     * Writes the stack top into a static field referenced by a [FieldRef].
     *
     * @param ref the static field reference to write.
     * @return this [CodeBuilder] for chaining.
     */
    fun putStatic(ref: FieldRef<*, *>) {
        popStack(ref.type)
        raw { putstatic(ref.owner.classDesc, ref.name, ref.type.classDesc) }
    }

    fun putField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        popStack(type)
        popStack(owner)
        raw { putfield(owner.classDesc, name, type.classDesc) }
    }

    /**
     * Writes the stack top into a field referenced by a [FieldRef].
     *
     * The caller is responsible for ensuring that the receiver object and value are already on the operand stack in
     * the order expected by `putfield`.
     *
     * @param ref the field reference to write.
     * @return this [CodeBuilder] for chaining.
     */
    fun putField(ref: FieldRef<*, *>) {
        if (ref.isStatic) putStatic(ref)
        else putField(ref.name, ref.owner, ref.type)
    }

    fun putThisField(ref: FieldRef<*, *>, valueBuilder: () -> Unit) {
        loadReceiver()
        valueBuilder()
        putField(ref)
    }

    fun build(db: CodeBuilder) {
        if (stackTypes.isNotEmpty()) {
            throw StackNotEmptyError(this)
        }

        if (!hasReturn) {
            throw NoReturnError(this)
        }

        for (instruction in instructions) db.instruction()
    }
}