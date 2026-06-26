package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.*
import pt.rafap.klassfile.utils.*
import java.lang.classfile.ClassFile.ACC_STATIC
import java.lang.classfile.CodeBuilder
import java.lang.constant.ConstantDescs
import kotlin.reflect.KClass


@Suppress("UNUSED")
@CodeScopeDsl
class CodeScope<O : Any, R : Any>(
    val scopeName: String,
    override val type: KlassDesc<R>,
    override val owner: KlassDesc<O>,
    val params: List<ParamRef<*>>,
) : TypedRef<O, R> {

    private var instructions = mutableListOf<CodeBuilder.() -> Unit>()
    private var stackTypes = mutableListOf<StackValue>()
    private var invocationTrace = mutableListOf<MethodRef<*, *>>()
    private var hasReturn = false

    fun printStackTypes() {
        if (stackTypes.isEmpty()) return

        println("Current Stack Types:")
        stackTypes.forEachIndexed { index, stackValue ->
            println("  [${index}]: $stackValue")
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

    private fun pushStack(stackValue: StackValue) {
        if (stackValue.type.classDesc == ConstantDescs.CD_void) return
        stackTypes.add(stackValue)
    }

    private fun pushStack(ref: ParamRef<*>) {
        pushStack(StackValue.Parameter(ref))
    }

    private fun pushStack(ref: FieldRef<*, *>) {
        pushStack(StackValue.Field(ref))
    }

    private fun pushStack(ref: MethodRef<*, *>) {
        pushStack(StackValue.ReturnValue(ref))
    }

    private fun popStack(): StackValue {
        return stackTypes.removeLastOrNull() ?: throw StackUnderflowError(this)
    }

    private fun popStack(expected: KlassDesc<*>): StackValue {
        if (expected.classDesc == ConstantDescs.CD_void) error("Cannot pop stack for void type in '${scopeName}'")
        expectTop(StackValue.NewObject(expected))
        return stackTypes.removeLast()
    }

    private fun stackInvoke(ref: MethodRef<*, *>) {
        invocationTrace.add(ref)
        for (param in ref.params.asReversed()) {
            popStack(param.type)
        }
        if (ref.invokeType != InvokeType.STATIC)
            popStack(ref.owner)

        pushStack(ref)
    }

    fun KlassDesc<*>.isAssignableFrom(other: KlassDesc<*>): Boolean {
        if (classDesc == other.classDesc)
            return true

        val expected = kClass.java
        val actual = other.kClass.java

        if (expected.isPrimitive || actual.isPrimitive)
            return false

        return expected.isAssignableFrom(actual)
    }

    private fun expectTop(expected: StackValue) {
        val actual = stackTypes.lastOrNull()
            ?: throw StackUnderflowError(this)

        if (!expected.type.isAssignableFrom(actual.type)) {
            throw StackTypeMismatchError(expected, actual, this)
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
        pushStack(StackValue.NewObject(type))
    }

    fun defaultCtor() {
        loadReceiver()
        val ownerKClass = owner.kClass
        if (!ownerKClass.isFinal && !ownerKClass.java.isInterface) {
            val ref = findMethod<O, Unit>(ConstantDescs.INIT_NAME, owner, klassDescOf()) {}
            invokeSpecial(ref)
        } else if (ownerKClass.java.isInterface) {
            val ref = findMethod(ConstantDescs.INIT_NAME, klassDescOf<Any>(), klassDescOf<Unit>()) {}
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

    fun inc(slot: Int, value: Int) {
        val param = getParam(slot)
        if (param.type.classDesc != ConstantDescs.CD_int) {
            throw UnsupportedOperationException("Increment operation is only supported for Int type parameters.")
        }

        raw { iinc(slot, value) }
    }

    inline fun <reified T : ParamRef<*>> inc(ref: T, value: Int = 1) {
        inc(ref.order, value)
    }

    fun dec(slot: Int, value: Int) {
        val param = getParam(slot)
        if (param.type.classDesc != ConstantDescs.CD_int) {
            throw UnsupportedOperationException("Decrement operation is only supported for Int type parameters.")
        }

        raw { iinc(slot, -value) }
    }

    inline fun <reified T : ParamRef<*>> dec(ref: T, value: Int) {
        dec(ref.order, value)
    }

    fun add() {
        val top = popStack()
        val topType = top.type.kClass
        val second = popStack()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)
        raw {
            when (topType) {
                Int::class -> iadd()
                Long::class -> ladd()
                Float::class -> fadd()
                Double::class -> dadd()
                else -> throw UnsupportedOperationException("Add operation not supported for type: $topType")
            }
        }
        pushStack(second)
    }

    fun add(a: ParamRef<*>, b: ParamRef<*>) {
        load(a)
        load(b)
        add()
    }

    operator fun <T : Any> ParamRef<T>.plus(ref: ParamRef<T>) = add(this, ref)

    fun sub() {
        val top = popStack()
        val topType = top.type.kClass
        val second = popStack()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        raw {
            when (topType) {
                Int::class -> isub()
                Long::class -> lsub()
                Float::class -> fsub()
                Double::class -> dsub()
                else -> throw UnsupportedOperationException("Sub operation not supported for type: $topType")
            }
        }
        pushStack(second)
    }

    fun sub(a: ParamRef<*>, b: ParamRef<*>) {
        load(a)
        load(b)
        sub()
    }

    operator fun <T : Any> ParamRef<T>.minus(ref: ParamRef<T>) = sub(this, ref)

    fun mul() {
        val top = popStack()
        val topType = top.type.kClass
        val second = popStack()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        raw {
            when (topType) {
                Int::class -> imul()
                Long::class -> lmul()
                Float::class -> fmul()
                Double::class -> dmul()
                else -> throw UnsupportedOperationException("Mul operation not supported for type: $topType")
            }
        }
        pushStack(second)
    }

    fun mul(a: ParamRef<*>, b: ParamRef<*>) {
        load(a)
        load(b)
        mul()
    }

    operator fun <T : Any> ParamRef<T>.times(ref: ParamRef<T>) = mul(this, ref)

    fun div() {
        val top = popStack()
        val topType = top.type.kClass
        val second = popStack()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        raw {
            when (topType) {
                Int::class -> idiv()
                Long::class -> ldiv()
                Float::class -> fdiv()
                Double::class -> ddiv()
                else -> throw UnsupportedOperationException("Div operation not supported for type: $topType")
            }
        }
        pushStack(second)
    }

    fun div(a: ParamRef<*>, b: ParamRef<*>) {
        load(a)
        load(b)
        div()
    }

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

        pushStack(StackValue.Constant(KlassDesc(kClass), constant))
    }

    fun nullValue() {
        raw { aconst_null() }
        pushStack(StackValue.Null())
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
        name: String? = null,
        noinline builder: ParameterScope.() -> Unit,
    ) = EagerDelegate { _, property ->
        findMethod(name ?: property.name, klassDescOf<O>(), klassDescOf<R>(), builder)
    }

    private fun assertInvokeType(expected: InvokeType, ref: MethodRef<*, *>) {
        if (expected != ref.invokeType)
            throw InvokeReferenceError(expected, ref)
    }

    fun invokeVirtual(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.VIRTUAL, ref)
        stackInvoke(ref)

        raw { invokevirtual(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    fun invokeInterface(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.INTERFACE, ref)
        stackInvoke(ref)

        raw { invokeinterface(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    fun invokeStatic(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.STATIC, ref)
        stackInvoke(ref)

        raw { invokestatic(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

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

    inline fun <reified O : Any, reified R : Any> invokeMethod(
        name: String,
        noinline builder: ParameterScope.() -> Unit = {},
    ) {

        val methodRef by findMethod<O, R>(name, builder)

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
        pushStack(StackValue.Field(FieldRef(name, owner, type, flags = ACC_STATIC)))
    }

    inline fun <reified O : Any, reified T : Any> getStatic(name: String) =
        getStatic(name, klassDescOf<O>(), klassDescOf<T>())

    fun getStatic(ref: FieldRef<*, *>) = getStatic(ref.name, ref.owner, ref.type)

    fun getField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        popStack(owner)
        raw { getfield(owner.classDesc, name, type.classDesc) }
        pushStack(StackValue.Field(FieldRef(name, owner, type, flags = 0)))
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

    fun pop() {
        val top = popStack()

        if (top.category != 1)
            error("pop requires a category 1 value.")

        raw { pop() }
    }

    fun pop2() {
        val top = popStack()

        if (top.category == 2) {
            raw { pop2() }
            return
        }

        val second = popStack()

        if (second.category != 1)
            error("Invalid stack shape for pop2.")

        raw { pop2() }
    }

    fun dup() {
        val top = popStack()

        if (top.category != 1)
            error("dup requires a category 1 value.")

        raw { dup() }

        pushStack(top)
        pushStack(top)
    }

    fun dupX1() {
        val v1 = popStack()
        val v2 = popStack()

        if (v1.category != 1 || v2.category != 1)
            error("dup_x1 requires two category 1 values.")

        raw { dup_x1() }

        pushStack(v1)
        pushStack(v2)
        pushStack(v1)
    }

    fun dupX2() {
        val v1 = popStack()

        if (v1.category != 1)
            error("dup_x2 requires a category 1 value on top.")

        val v2 = popStack()

        if (v2.category == 2) {
            raw { dup_x2() }

            pushStack(v1)
            pushStack(v2)
            pushStack(v1)
            return
        }

        val v3 = popStack()

        if (v2.category != 1 || v3.category != 1)
            error("Invalid stack shape for dup_x2.")

        raw { dup_x2() }

        pushStack(v1)
        pushStack(v3)
        pushStack(v2)
        pushStack(v1)
    }

    fun dup2() {
        val v1 = popStack()

        if (v1.category == 2) {
            raw { dup2() }

            pushStack(v1)
            pushStack(v1)
            return
        }

        val v2 = popStack()

        if (v2.category != 1)
            error("Invalid stack shape for dup2.")

        raw { dup2() }

        pushStack(v2)
        pushStack(v1)
        pushStack(v2)
        pushStack(v1)
    }

    fun dup2X1() {
        val v1 = popStack()

        if (v1.category == 2) {
            val v2 = popStack()

            if (v2.category != 1)
                error("Invalid stack shape for dup2_x1.")

            raw { dup2_x1() }

            pushStack(v1)
            pushStack(v2)
            pushStack(v1)
            return
        }

        val v2 = popStack()
        val v3 = popStack()

        if (v2.category != 1 || v3.category != 1)
            error("Invalid stack shape for dup2_x1.")

        raw { dup2_x1() }

        pushStack(v2)
        pushStack(v1)
        pushStack(v3)
        pushStack(v2)
        pushStack(v1)
    }

    fun dup2X2() {
        val v1 = popStack()

        when (v1.category) {
            2 -> {
                val v2 = popStack()

                if (v2.category == 2) {
                    raw { dup2_x2() }

                    pushStack(v1)
                    pushStack(v2)
                    pushStack(v1)
                } else {
                    val v3 = popStack()

                    if (v3.category != 1)
                        error("Invalid stack shape for dup2_x2.")

                    raw { dup2_x2() }

                    pushStack(v1)
                    pushStack(v3)
                    pushStack(v2)
                    pushStack(v1)
                }
            }

            1 -> {
                val v2 = popStack()

                if (v2.category != 1)
                    error("Invalid stack shape for dup2_x2.")

                val v3 = popStack()

                if (v3.category == 2) {
                    raw { dup2_x2() }

                    pushStack(v2)
                    pushStack(v1)
                    pushStack(v3)
                    pushStack(v2)
                    pushStack(v1)
                } else {
                    val v4 = popStack()

                    if (v3.category != 1 || v4.category != 1)
                        error("Invalid stack shape for dup2_x2.")

                    raw { dup2_x2() }

                    pushStack(v2)
                    pushStack(v1)
                    pushStack(v4)
                    pushStack(v3)
                    pushStack(v2)
                    pushStack(v1)
                }
            }
        }
    }

    fun swap() {
        val v1 = popStack()
        val v2 = popStack()

        if (v1.category != 1 || v2.category != 1)
            error("swap requires two category 1 values.")

        raw { swap() }

        pushStack(v1)
        pushStack(v2)
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