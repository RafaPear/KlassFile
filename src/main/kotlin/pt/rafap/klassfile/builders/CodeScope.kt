package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.*
import pt.rafap.klassfile.utils.*
import java.lang.classfile.ClassFile.ACC_STATIC
import java.lang.classfile.CodeBuilder
import java.lang.constant.ConstantDescs
import kotlin.reflect.KClass


@Suppress("UNUSED")
/**
 * Emits bytecode for a single method body.
 *
 * The scope collects low-level [CodeBuilder] instructions, tracks invocation
 * metadata for validation, and exposes helpers for common bytecode patterns.
 */
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

    /** Prints the tracked value trace for debugging. */
    fun printStackTypes() {
        if (stackTypes.isEmpty()) return

        println("Current Stack Types:")
        stackTypes.forEachIndexed { index, stackValue ->
            println("  [${index}]: $stackValue")
        }
    }

    /** Prints the tracked method-invocation trace for debugging. */
    fun printInvocationTrace() {
        if (invocationTrace.isEmpty()) return

        println("Current Invocation Trace:")
        invocationTrace.forEachIndexed { index, methodRef ->
            println("  [${index}]: $methodRef")
        }
    }

    /** Prints the tracked value and invocation traces for debugging. */
    fun printStack() {
        printStackTypes()
        printInvocationTrace()
    }

    /** Records a value in the internal value trace when applicable. */
    private fun pushStack(stackValue: StackValue) {
        if (stackValue.type.classDesc == ConstantDescs.CD_void) return
        stackTypes.add(stackValue)
    }

    /** Records a parameter value in the internal value trace. */
    private fun pushStack(ref: ParamRef<*>) {
        pushStack(StackValue.Parameter(ref))
    }

    /** Records a field value in the internal value trace. */
    private fun pushStack(ref: FieldRef<*, *>) {
        pushStack(StackValue.Field(ref))
    }

    /** Records a method return value in the internal value trace. */
    private fun pushStack(ref: MethodRef<*, *>) {
        pushStack(StackValue.ReturnValue(ref))
    }

    /**
     * Removes and returns the most recent tracked value.
     *
     * @return the most recent tracked value.
     * @throws StackUnderflowError when no values are available.
     */
    private fun popStack(): StackValue {
        return stackTypes.removeLastOrNull() ?: throw StackUnderflowError(this)
    }

    /**
     * Removes and returns the most recent tracked value after a type check.
     *
     * @param expected the expected value type.
     * @return the removed value.
     */
    private fun popStack(expected: KlassDesc<*>): StackValue {
        if (expected.classDesc == ConstantDescs.CD_void) error("Cannot pop stack for void type in '${scopeName}'")
        expectTop(StackValue.NewObject(expected))
        return stackTypes.removeLast()
    }

    /** Applies invocation bookkeeping for a method call. */
    private fun stackInvoke(ref: MethodRef<*, *>) {
        invocationTrace.add(ref)
        for (param in ref.params.asReversed()) {
            popStack(param.type)
        }
        if (ref.invokeType != InvokeType.STATIC)
            popStack(ref.owner)

        pushStack(ref)
    }

    /**
     * Returns whether this descriptor accepts [other] by JVM assignability rules.
     *
     * @param other the candidate descriptor.
     * @return `true` when [other] can be assigned to this descriptor.
     */
    private fun KlassDesc<*>.isAssignableFrom(other: KlassDesc<*>): Boolean {
        if (classDesc == other.classDesc)
            return true

        val expected = kClass.java
        val actual = other.kClass.java

        if (expected.isPrimitive || actual.isPrimitive)
            return false

        return expected.isAssignableFrom(actual)
    }

    /** Validates the most recent tracked value against [expected]. */
    private fun expectTop(expected: StackValue) {
        val actual = stackTypes.lastOrNull()
            ?: throw StackUnderflowError(this)

        if (!expected.type.isAssignableFrom(actual.type)) {
            throw StackTypeMismatchError(expected, actual, this)
        }
    }

    private var isInsideRawBlock = false

    /**
     * Adds a raw bytecode emission block to the instruction list.
     *
     * @param block the low-level code emission block.
     * @throws NestedRawBlockError when a raw block is opened from inside another raw block.
     */
    private fun raw(block: CodeBuilder.() -> Unit) {
        if (isInsideRawBlock) throw NestedRawBlockError()
        isInsideRawBlock = true
        instructions.add(block)
        isInsideRawBlock = false
    }

    /**
     * Emits a `new` instruction for the given type.
     *
     * @param type the type to instantiate.
     */
    fun <T : Any> new(type: KlassDesc<T>) {
        raw { new_(type.classDesc) }
        pushStack(StackValue.NewObject(type))
    }

    /**
     * Emits the default constructor invocation for the current receiver or superclass.
     *
     * @throws NoConstructorError when the owning type cannot be constructed.
     */
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

    /** Emits the appropriate return instruction for the declared return type. */
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

    /**
     * Resolves a parameter by local slot index.
     *
     * @param slot the local slot index.
     * @return the matching parameter reference.
     * @throws InvalidSlotIndexError if no parameter is mapped to [slot].
     */
    private fun getParam(slot: Int) = params.firstOrNull { it.order == slot }
        ?: throw InvalidSlotIndexError(slot)

    /**
     * Loads the value stored in a local slot.
     *
     * @param slot the local slot index.
     * @throws InvalidSlotIndexError if the slot does not exist.
     */
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

    /**
     * Loads the implicit receiver parameter.
     *
     * @throws NoParamFoundError if the current scope does not define a receiver.
     */
    fun loadReceiver() {
        val param = params.firstOrNull { it is ParamRef.ReceiverRef<*> }
            ?: throw NoParamFoundError("receiver")
        load(param.order)
    }

    /**
     * Stores a value into a local slot.
     *
     * @param slot the local slot index.
     * @throws InvalidSlotIndexError if the slot does not exist.
     */
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

    /** Loads a local slot by reference. */
    inline fun <reified T : ParamRef<*>> load(ref: T) = load(ref.order)


    /** Stores a value into a local slot by reference. */
    inline fun <reified T : ParamRef<*>> store(ref: T) = store(ref.order)

    /**
     * Increments an integer local slot in place.
     *
     * @param slot the local slot index.
     * @param value the amount to add.
     * @throws UnsupportedOperationException if the slot is not backed by an `Int`.
     */
    fun inc(slot: Int, value: Int) {
        val param = getParam(slot)
        if (param.type.classDesc != ConstantDescs.CD_int) {
            throw UnsupportedOperationException("Increment operation is only supported for Int type parameters.")
        }

        raw { iinc(slot, value) }
    }

    /** Increments an integer parameter or slot in place. */
    inline fun <reified T : ParamRef<*>> inc(ref: T, value: Int = 1) =
        inc(ref.order, value)


    /**
     * Decrements an integer local slot in place.
     *
     * @param slot the local slot index.
     * @param value the amount to subtract.
     * @throws UnsupportedOperationException if the slot is not backed by an `Int`.
     */
    fun dec(slot: Int, value: Int) {
        val param = getParam(slot)
        if (param.type.classDesc != ConstantDescs.CD_int) {
            throw UnsupportedOperationException("Decrement operation is only supported for Int type parameters.")
        }

        raw { iinc(slot, -value) }
    }

    /** Decrements an integer parameter or slot in place. */
    inline fun <reified T : ParamRef<*>> dec(ref: T, value: Int) {
        dec(ref.order, value)
    }

    /**
     * Adds two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
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

    /**
     * Loads a value from either a parameter or field reference.
     *
     * @param ref the reference to load.
     */
    fun loadRef(ref: TypedRef<*, *>) {
        when (ref) {
            is ParamRef<*> -> load(ref)
            is FieldRef<*, *> -> {
                loadReceiver()
                getField(ref)
            }
            else -> throw UnsupportedOperationException("Unsupported TypedRef type: ${ref::class.simpleName}")
        }
    }

    /** Loads two values and adds them. */
    fun add(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        add()
    }

    /**
     * Subtracts two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
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

    /** Loads two values and subtracts them. */
    fun sub(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        sub()
    }

    /**
     * Multiplies two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
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

    /** Loads two values and multiplies them
     *
     *
     * */
    fun mul(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        mul()
    }

    /**
     * Divides two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
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

    /** Loads two values and divides them. */
    fun div(a: TypedRef<*,*>, b: TypedRef<*,*>) {
        loadRef(a)
        loadRef(b)
        div()
    }

    /**
     * Loads a constant using an explicit Kotlin type.
     *
     * @param constant the value to load, or `null` for a class literal.
     * @param kClass the Kotlin type associated with the value.
     */
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

    /** Emits `aconst_null`. */
    fun nullValue() {
        raw { aconst_null() }
        pushStack(StackValue.Null())
    }

    /**
     * Loads a constant through the constant pool.
     *
     * Supported values are [String], [Int], [Long], [Float], and [Double]. Any
     * other type is treated as a class literal.
     *
     * @param constant the constant value to load.
     * @throws IllegalArgumentException if the type of [constant] is not supported.
     */
    inline fun <reified T : Any> ldc(constant: T? = null) = ldc(constant, T::class)

    /**
     * Resolves a method reference using an explicit owner and return type.
     *
     * @param name the method name to resolve.
     * @param owner the class that owns the method.
     * @param returnType the expected return type.
     * @param builder additional parameter metadata used for overload resolution.
     * @return the resolved method reference.
     */
    fun <O : Any, R : Any> findMethod(
        name: String,
        owner: KlassDesc<O>,
        returnType: KlassDesc<R>,
        builder: ParameterScope.() -> Unit,
    ): MethodRef<O, R> {
        val params = ParameterScope().apply(builder).build().toTypedArray()
        return resolveMethod(name, owner, returnType, *params)
    }

    /** Lazily resolves a method reference using the current property name when omitted. */
    inline fun <reified O : Any, reified R : Any> findMethod(
        name: String? = null,
        noinline builder: ParameterScope.() -> Unit,
    ) = EagerDelegate { _, property ->
        findMethod(name ?: property.name, klassDescOf<O>(), klassDescOf<R>(), builder)
    }

    /**
     * Ensures a method reference matches the expected invocation kind.
     *
     * @param expected the required invocation type.
     * @param ref the method reference to validate.
     * @throws InvokeReferenceError if invocation kinds differ.
     */
    private fun assertInvokeType(expected: InvokeType, ref: MethodRef<*, *>) {
        if (expected != ref.invokeType)
            throw InvokeReferenceError(expected, ref)
    }

    /**
     * Emits an `invokevirtual` call for the given method reference.
     *
     * @param ref the method reference to invoke.
     * @throws InvokeReferenceError if the reference does not use the expected invocation kind.
     */
    fun invokeVirtual(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.VIRTUAL, ref)
        stackInvoke(ref)

        raw { invokevirtual(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /** Emits an `invokeinterface` call for the given method reference. */
    fun invokeInterface(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.INTERFACE, ref)
        stackInvoke(ref)

        raw { invokeinterface(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /** Emits an `invokestatic` call for the given method reference. */
    fun invokeStatic(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.STATIC, ref)
        stackInvoke(ref)

        raw { invokestatic(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /** Emits an `invokespecial` call for the given method reference. */
    fun invokeSpecial(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.SPECIAL, ref)
        stackInvoke(ref)

        raw { invokespecial(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /**
     * Instantiates a type and calls its constructor.
     *
     * @param owner the type to instantiate.
     * @param builder parameter metadata used to resolve the constructor.
     */
    fun <O : Any> instantiate(
        owner: KlassDesc<O>,
        builder: ParameterScope.() -> Unit = {},
    ) {
        new(owner)
        dup()
        val ref = findMethod<O, Unit>(ConstantDescs.INIT_NAME, owner, klassDescOf(), builder)
        invokeSpecial(ref)
    }

    /** Instantiates a reified type and calls its constructor. */
    inline fun <reified O : Any> instantiate(
        noinline builder: ParameterScope.() -> Unit = {},
    ) {
        instantiate(klassDescOf<O>(), builder)
    }

    /**
     * Instantiates a value and stores it directly into the referenced field.
     *
     * @param fieldRef the field to initialize.
     * @param builder parameter metadata used to resolve the constructor.
     */
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
     * members use `invokevirtual`. When the reference targets the class currently being built, `this` is loaded before
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

    /**
     * Invokes a method by name.
     *
     * @param name the method name, or `null` to use the backing property name.
     * @param builder additional parameter metadata used for overload resolution.
     */
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

    /** Replays a method invocation using the reference's own dispatch kind. */
    operator fun MethodRef<*, *>.invoke() {
        invokeMethod(this)
    }

    /**
     * Emits a `getstatic` instruction.
     *
     * @param name the field name.
     * @param owner the class that owns the field.
     * @param type the field type.
     */
    fun getStatic(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        raw { getstatic(owner.classDesc, name, type.classDesc) }
        pushStack(StackValue.Field(FieldRef(name, owner, type, flags = ACC_STATIC)))
    }

    /**
     * Emits `getstatic` using reified owner and field type parameters.
     *
     * @param name the field name.
     */
    inline fun <reified O : Any, reified T : Any> getStatic(name: String) =
        getStatic(name, klassDescOf<O>(), klassDescOf<T>())

    /** Emits `getstatic` from an existing field reference. */
    fun getStatic(ref: FieldRef<*, *>) = getStatic(ref.name, ref.owner, ref.type)

    /** Emits a `getfield` instruction. */
    fun getField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        popStack(owner)
        raw { getfield(owner.classDesc, name, type.classDesc) }
        pushStack(StackValue.Field(FieldRef(name, owner, type, flags = 0)))
    }

    /** Loads either a static or instance field based on the reference metadata. */
    fun getField(ref: FieldRef<*, *>) {
        if (ref.isStatic) getStatic(ref)
        else getField(ref.name, ref.owner, ref.type)
    }

    /** Emits a `putstatic` instruction for the given field reference. */
    fun putStatic(ref: FieldRef<*, *>) {
        popStack(ref.type)
        raw { putstatic(ref.owner.classDesc, ref.name, ref.type.classDesc) }
    }

    /** Emits a `putfield` instruction. */
    fun putField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        popStack(type)
        popStack(owner)
        raw { putfield(owner.classDesc, name, type.classDesc) }
    }

    /** Emits `putfield` or `putstatic` depending on the field metadata. */
    fun putField(ref: FieldRef<*, *>) {
        if (ref.isStatic) putStatic(ref)
        else putField(ref.name, ref.owner, ref.type)
    }

    /** Loads the receiver, evaluates the value block, and writes the result to the field. */
    fun putThisField(ref: FieldRef<*, *>, valueBuilder: () -> Unit) {
        loadReceiver()
        valueBuilder()
        putField(ref)
    }

    /** Emits `pop`. */
    fun pop() {
        val top = popStack()

        if (top.category != 1)
            error("pop requires a category 1 value.")

        raw { pop() }
    }

    /** Emits `pop2`. */
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

    /** Emits `dup`. */
    fun dup() {
        val top = popStack()

        if (top.category != 1)
            error("dup requires a category 1 value.")

        raw { dup() }

        pushStack(top)
        pushStack(top)
    }

    /** Emits `dup_x1`. */
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

    /** Emits `dup_x2`. */
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

    /** Emits `dup2`. */
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

    /** Emits `dup2_x1`. */
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

    /** Emits `dup2_x2`. */
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

    /** Emits `swap`. */
    fun swap() {
        val v1 = popStack()
        val v2 = popStack()

        if (v1.category != 1 || v2.category != 1)
            error("swap requires two category 1 values.")

        raw { swap() }

        pushStack(v1)
        pushStack(v2)
    }

    /**
     * Finalizes the code block and writes the collected instructions to [db].
     *
     * @param db the code builder receiving the emitted instructions.
     * @throws StackNotEmptyError if there are unresolved values left over.
     * @throws NoReturnError if no return instruction was emitted.
     */
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