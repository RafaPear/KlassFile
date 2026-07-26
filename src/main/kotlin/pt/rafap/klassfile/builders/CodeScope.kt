package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.*
import pt.rafap.klassfile.models.StackType.Companion.stackType
import pt.rafap.klassfile.utils.*
import java.lang.classfile.ClassFile.ACC_STATIC
import java.lang.classfile.CodeBuilder
import java.lang.classfile.TypeKind
import java.lang.constant.ConstantDescs
import kotlin.random.Random
import kotlin.reflect.KClass


@Suppress("UNUSED")
/**
 * Emits bytecode for a single method body.
 *
 * The scope collects low-level [CodeBuilder] instructions, tracks invocation
 * metadata for validation, and exposes helpers for common bytecode patterns.
 */
@CodeScopeDsl
@KlassFileApi
class CodeScope<O : Any, R : Any>(
    val scopeName: String,
    override val type: KlassDesc<R>,
    override val owner: KlassDesc<O>,
    val params: List<ParamRef<*>>,
) : TypedRef<O, R> {

    private val locals = LocalsStorage(params)
    private var instructions = mutableListOf<CodeBuilder.() -> Unit>()
    private var stack = Stack(this)
    private var hasReturn = false

    /** Prints the tracked value and invocation traces for debugging. */
    fun printInfo() {
        locals.print()
        stack.print()
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

    fun <T : Any> local(name: String, type: KlassDesc<T>) = locals.addLocal(name, type)

    inline fun <reified T : Any> local(): EagerDelegate<LocalRef<T>> =
        EagerDelegate { _, prop ->
            local(prop.name, klassDescOf<T>())
        }

    inline infix fun <reified T : Any> LocalRef<T>.set(value: T) {
        ldc(value)
        store(this)
    }

    inline infix fun <reified T : Any> LocalRef<T>.set(value: OrderedRef<T>) {
        load(value)
        store(this)
    }

    infix fun LocalRef<*>.set(value: Unit) {
        store(this)
    }

    /**
     * Emits a `new` instruction for the given type.
     *
     * @param type the type to instantiate.
     */
    fun <T : Any> new(type: KlassDesc<T>) {
        raw { new_(type.classDesc) }
        stack.push(StackValue.Reference.NewObject(type))
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
        if (type.stackType != StackType.VOID)
            stack.pop(type)

        raw {
            when (type.stackType) {
                StackType.INT -> ireturn()
                StackType.LONG -> lreturn()
                StackType.FLOAT -> freturn()
                StackType.DOUBLE -> dreturn()
                StackType.REFERENCE -> areturn()
                StackType.VOID -> return_()
            }
        }
        stack.setUnreachable()
    }

    /**
     * Loads the value stored in a local slot.
     *
     * @param slot the local slot index.
     * @throws InvalidSlotIndexError if the slot does not exist.
     */
    fun load(slot: Int) {
        val param = locals.getLocal(slot)
        raw {
            when (param.type.classDesc) {
                ConstantDescs.CD_int -> iload(slot)
                ConstantDescs.CD_long -> lload(slot)
                ConstantDescs.CD_float -> fload(slot)
                ConstantDescs.CD_double -> dload(slot)
                else -> aload(slot)
            }
        }
        stack.push(param)
    }

    /** Loads a local slot by reference. */
    inline fun <reified T : OrderedRef<*>> load(ref: T) = load(ref.order)

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
        val param = locals.getLocal(slot)
        stack.pop(param.type)

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


    /** Stores a value into a local slot by reference. */
    inline fun <reified T : OrderedRef<*>> store(ref: T) = store(ref.order)

    /**
     * Increments an integer local slot in place.
     *
     * @param slot the local slot index.
     * @param value the amount to add.
     * @throws UnsupportedOperationException if the slot is not backed by an `Int`.
     */
    fun inc(slot: Int, value: Int) {
        val param = locals.getLocal(slot)
        if (param.type.classDesc != ConstantDescs.CD_int) {
            throw UnsupportedOperationException("Increment operation is only supported for Int type parameters.")
        }

        raw { iinc(slot, value) }
    }

    /** Increments an integer parameter or slot in place. */
    fun inc(ref: OrderedRef<Int>, value: Int = 1) = inc(ref.order, value)

    /**
     * Decrements an integer local slot in place.
     *
     * @param slot the local slot index.
     * @param value the amount to subtract.
     * @throws UnsupportedOperationException if the slot is not backed by an `Int`.
     */
    fun dec(slot: Int, value: Int) {
        val param = locals.getLocal(slot)
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
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Int::class -> raw { iadd() }
            Long::class -> raw { ladd() }
            Float::class -> raw { fadd() }
            Double::class -> raw { dadd() }
            else -> throw UnsupportedOperationException("Add operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
    }

    /**
     * Loads a value from either a parameter or field reference.
     *
     * @param ref the reference to load.
     */
    fun loadRef(ref: TypedRef<*, *>) {
        when (ref) {
            is OrderedRef<*> -> load(ref)
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
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)


        when (topType) {
            Int::class -> raw { isub() }
            Long::class -> raw { lsub() }
            Float::class -> raw { fsub() }
            Double::class -> raw { dsub() }
            else -> throw UnsupportedOperationException("Sub operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
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
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Int::class -> raw { imul() }
            Long::class -> raw { lmul() }
            Float::class -> raw { fmul() }
            Double::class -> raw { dmul() }
            else -> throw UnsupportedOperationException("Mul operation not supported for type: ${topType.simpleName}")
        }
        stack.push(second)
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
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)


        when (topType) {
            Int::class -> raw { idiv() }
            Long::class -> raw { ldiv() }
            Float::class -> raw { fdiv() }
            Double::class -> raw { ddiv() }
            else -> throw UnsupportedOperationException("Div operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
    }

    /** Loads two values and divides them. */
    fun div(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        div()
    }

    /**
     * Calculates the remainder of two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun rem() {
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Int::class -> raw { irem() }
            Long::class -> raw { lrem() }
            Float::class -> raw { frem() }
            Double::class -> raw { drem() }
            else -> throw UnsupportedOperationException("Rem operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
    }

    /** Loads two values and takes the remainder. */
    fun rem(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        rem()
    }

    /**
     * Calculates the negative of a numeric value.
     *
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun neg() {
        val top = stack.pop()
        when (val topType = top.type.kClass) {
            Int::class -> raw { ineg() }
            Long::class -> raw { lneg() }
            Float::class -> raw { fneg() }
            Double::class -> raw { dneg() }
            else -> throw UnsupportedOperationException("Neg operation not supported for type: ${topType.simpleName}")
        }

        stack.push(top)
    }

    /** Takes the negative of a value */
    fun neg(a: TypedRef<*, *>) {
        loadRef(a)
        neg()
    }

    /**
     * Loads two values and shifts the first left by the second.
     *
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun shl() {
        // Shift
        stack.pop(klassDescOf<Int>())

        val top = stack.pop()
        val topType = top.type.kClass

        when (topType) {
            Int::class -> raw { ishl() }
            Long::class -> raw { lshl() }
            else -> throw UnsupportedOperationException("Shl operation not supported for type: ${topType.simpleName}")
        }

        stack.push(top)
    }

    /** Loads two values and shifts the first left by the second. */
    fun shl(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        shl()
    }

    /** Loads two values and shifts the first left by the second. */
    fun shl(a: TypedRef<*, *>, shift: Int) {
        loadRef(a)
        ldc(shift)
        shl()
    }

    /**
     * Loads two values and shifts the first right by the second.
     *
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun shr() {
        // Shift
        stack.pop(klassDescOf<Int>())

        val top = stack.pop()
        val topType = top.type.kClass

        when (topType) {
            Int::class -> raw { ishr() }
            Long::class -> raw { lshr() }
            else -> throw UnsupportedOperationException("Shr operation not supported for type: ${topType.simpleName}")
        }

        stack.push(top)
    }

    /** Loads two values and shifts the first right by the second. */
    fun shr(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        shr()
    }

    /** Loads two values and shifts the first right by the second. */
    fun shr(a: TypedRef<*, *>, shift: Int) {
        loadRef(a)
        ldc(shift)
        shr()
    }

    /**
     * Loads two values and shifts the first right by the second.
     *
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun uShr() {
        // Shift
        stack.pop(klassDescOf<Int>())

        val top = stack.pop()
        val topType = top.type.kClass

        when (topType) {
            Int::class -> raw { iushr() }
            Long::class -> raw { lushr() }
            else -> throw UnsupportedOperationException("uShr operation not supported for type: ${topType.simpleName}")
        }

        stack.push(top)
    }

    /** Loads two values and shifts the first right by the second. */
    fun uShr(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        uShr()
    }

    /** Loads two values and shifts the first right by the second. */
    fun uShr(a: TypedRef<*, *>, shift: Int) {
        loadRef(a)
        ldc(shift)
        uShr()
    }

    /**
     * Calculates the bitwise AND of two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun and() {
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Int::class -> raw { iand() }
            Long::class -> raw { land() }
            else -> throw UnsupportedOperationException("And operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
    }

    /** Loads two values and calculates the bitwise AND. */
    fun and(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        and()
    }

    /**
     * Calculates the bitwise OR of two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun or() {
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Int::class -> raw { ior() }
            Long::class -> raw { lor() }
            else -> throw UnsupportedOperationException("Or operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
    }

    /** Loads two values or calculates the bitwise OR. */
    fun or(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        or()
    }

    /**
     * Calculates the bitwise XOR of two numeric values.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun xor() {
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Int::class -> raw { ixor() }
            Long::class -> raw { lxor() }
            else -> throw UnsupportedOperationException("Xor operation not supported for type: ${topType.simpleName}")
        }

        stack.push(second)
    }

    /** Loads two values xor calculates the bitwise XOR. */
    fun xor(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        xor()
    }

    /**
     * Converts the stack top value to the destination type.
     *
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun convert(destType: KClass<*>) {
        val top = stack.pop()
        val topType = top.type.kClass

        when (topType) {
            Int::class -> {
                when (destType) {
                    Long::class -> raw { i2l() }
                    Float::class -> raw { i2f() }
                    Double::class -> raw { i2d() }
                    Byte::class -> raw { i2b() }
                    Char::class -> raw { i2c() }
                    Short::class -> raw { i2s() }
                    else -> throw UnsupportedOperationException("Conversion from Int to ${destType.simpleName} is not supported.")
                }
            }

            Long::class -> {
                when (destType) {
                    Int::class -> raw { l2i() }
                    Float::class -> raw { l2f() }
                    Double::class -> raw { l2d() }
                    else -> throw UnsupportedOperationException("Conversion from Long to ${destType.simpleName} is not supported.")
                }
            }

            Float::class -> {
                when (destType) {
                    Int::class -> raw { f2i() }
                    Long::class -> raw { f2l() }
                    Double::class -> raw { f2d() }
                    else -> throw UnsupportedOperationException("Conversion from Float to ${destType.simpleName} is not supported.")
                }
            }

            Double::class -> {
                when (destType) {
                    Int::class -> raw { d2i() }
                    Long::class -> raw { d2l() }
                    Float::class -> raw { d2f() }
                    else -> throw UnsupportedOperationException("Conversion from Double to ${destType.simpleName} is not supported.")
                }
            }

            else -> throw UnsupportedOperationException("Conversion from ${topType.simpleName} to ${destType.simpleName} is not supported.")
        }

        val resultType = when (destType) {
            Byte::class, Short::class, Char::class -> Int::class
            else -> destType
        }

        stack.push(top.withType(KlassDesc(resultType)))
    }

    inline fun <reified T : Any> convert() = convert(T::class)

    /**
     * Compares two numeric values on the stack and pushes an integer result.
     *
     * @throws StackTypeMismatchError if the operand types differ.
     * @throws UnsupportedOperationException if the operand type is not supported.
     */
    fun cmp() {
        val top = stack.pop()
        val topType = top.type.kClass
        val second = stack.pop()
        val secondType = second.type.kClass

        if (topType != secondType)
            throw StackTypeMismatchError(second, top, this)

        when (topType) {
            Float::class -> raw { fcmpl() }
            Double::class -> raw { dcmpl() }
            Long::class -> raw { lcmp() }
            else -> throw UnsupportedOperationException("Cmp operation not supported for type: ${topType.simpleName}")
        }

        stack.push(top.withType(KlassDesc(Int::class)))
    }

    /** Loads two values and compares them. */
    fun cmp(a: TypedRef<*, *>, b: TypedRef<*, *>) {
        loadRef(a)
        loadRef(b)
        cmp()
    }

    fun goto(label: LabelRef) {
        label.performGoto(stack, this)

        raw { goto_(label.getLabel()) }
    }

    fun label(name: String): LabelRef {
        val ref = LabelRef(name)
        raw { ref.setLabel(newLabel()) }
        return ref
    }

    fun label(): EagerDelegate<LabelRef> = EagerDelegate { _, param -> label(param.name) }

    fun LabelRef.bind() {
        if (isBound()) instructions.removeAt(boundIdx)
        performBiding(stack, instructions.size)
        setBound()

        raw { labelBinding(getLabel()) }
    }

    fun KlassDesc<*>.toTypeKind(): TypeKind? =
        when (kClass) {
            Boolean::class -> TypeKind.BOOLEAN
            Byte::class -> TypeKind.BYTE
            Char::class -> TypeKind.CHAR
            Short::class -> TypeKind.SHORT
            Int::class -> TypeKind.INT
            Long::class -> TypeKind.LONG
            Float::class -> TypeKind.FLOAT
            Double::class -> TypeKind.DOUBLE
            else -> null
        }

    fun newArray(type: KlassDesc<*>) {
        stack.pop(klassDescOf<Int>())

        val kind = type.toTypeKind()

        if (kind != null) raw { newarray(kind) }
        else raw { anewarray(type.classDesc) }

        stack.push(StackValue.Reference.NewArrayObject(type))
    }

    fun newMultiArray(type: KlassDesc<*>, dimensions: Int) {
        TODO("Not yet implemented: newMultiArray for type ${type.kClass.simpleName} with $dimensions dimensions")
    }

    inline fun <reified T : Any> newArray() =
        newArray(klassDescOf<T>())

    private fun popArray(): KlassDesc.ArrayKlassDesc<*> {
        val value = stack.pop()

        val array = value.type
        if (array !is KlassDesc.ArrayKlassDesc<*>) {
            throw StackTypeMismatchError(
                klassDescOf<Array<*>>(),
                value.type,
                this
            )
        }

        return array
    }

    fun arrayLength() {
        popArray()
        raw { arraylength() }
        stack.push(StackValue.KnownType(klassDescOf<Int>()))
    }

    fun ParamRef<*>.length() {
        load(this)
        arrayLength()
    }

    /**
     * Loads a value from an array at the specified index.
     *
     * ORDER:
     * - The array reference is on top of the stack.
     * - The index is below the array reference.
     *
     * @throws StackTypeMismatchError if the array type is not an array.
     */
    fun arrayLoad() {
        stack.pop(klassDescOf<Int>()) // idx
        val elementType = popArray().elementType

        when (elementType.kClass) {
            Int::class -> raw { iaload() }
            Long::class -> raw { laload() }
            Float::class -> raw { faload() }
            Double::class -> raw { daload() }
            Byte::class, Boolean::class -> raw { baload() }
            Char::class -> raw { caload() }
            Short::class -> raw { saload() }
            else -> raw { aaload() }
        }

        stack.push(StackValue.KnownType(elementType))
    }

    operator fun <T : Any> OrderedRef<T>.get(idx: Int) {
        load(this)
        ldc(idx)
        arrayLoad()
    }

    operator fun <T : Any> OrderedRef<T>.get(ref: OrderedRef<Int>) {
        load(this)
        load(ref)
        arrayLoad()
    }

    inline operator fun <reified T : Any> OrderedRef<*>.set(idx: Int, value: T) {
        load(this)
        ldc(idx)
        ldc(value)
        arrayStore()
    }

    inline operator fun <reified T : Any> OrderedRef<*>.set(idx: OrderedRef<Int>, value: T) {
        load(this)
        load(idx)
        ldc(value)
        arrayStore()
    }

    /**
     * Stores a value into an array at the specified index.
     *
     * ORDER: Value -> Idx -> Array
     *
     * @throws StackTypeMismatchError if the value type does not match the array element type.
     */
    fun arrayStore() {
        val value = stack.pop()
        stack.pop(klassDescOf<Int>()) // idx
        val elementType = popArray().elementType

        if (elementType != value.type)
            throw StackTypeMismatchError(
                elementType,
                value.type,
                this
            )

        when (elementType.kClass) {
            Int::class -> raw { iastore() }
            Long::class -> raw { lastore() }
            Float::class -> raw { fastore() }
            Double::class -> raw { dastore() }
            Byte::class, Boolean::class -> raw { bastore() }
            Char::class -> raw { castore() }
            Short::class -> raw { sastore() }
            else -> raw { aastore() }
        }
    }

    fun checkCast(type: KlassDesc<*>) {
        val value = stack.pop()

        raw { checkcast(type.classDesc) }

        stack.push(value.withType(type))
    }

    inline fun <reified T : Any> checkCast() =
        checkCast(klassDescOf<T>())

    fun instanceOf(type: KlassDesc<*>) {
        stack.pop()

        raw { instanceOf(type.classDesc) }

        stack.push(StackValue.KnownType(klassDescOf<Int>()))
    }

    inline fun <reified T : Any> instanceOf() =
        instanceOf(klassDescOf<T>())

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

        stack.push(StackValue.Constant(KlassDesc(kClass), constant))
    }

    /** Emits `aconst_null`. */
    fun nullValue() {
        raw { aconst_null() }
        stack.push(StackValue.Reference.Null())
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
        builder: ArgumentScope.() -> Unit,
    ): MethodRef<O, R> {
        val params = ArgumentScope().apply(builder).build().toTypedArray()
        return resolveMethod(name, owner, returnType, *params)
    }

    /** Lazily resolves a method reference using the current property name when omitted. */
    inline fun <reified O : Any, reified R : Any> findMethod(
        name: String? = null,
        noinline builder: ArgumentScope.() -> Unit,
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
        stack.stackInvoke(ref)

        raw { invokevirtual(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /** Emits an `invokeinterface` call for the given method reference. */
    fun invokeInterface(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.INTERFACE, ref)
        stack.stackInvoke(ref)

        raw { invokeinterface(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /** Emits an `invokestatic` call for the given method reference. */
    fun invokeStatic(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.STATIC, ref)
        stack.stackInvoke(ref)

        raw { invokestatic(ref.owner.classDesc, ref.name, ref.methodTypeDesc) }
    }

    /** Emits an `invokespecial` call for the given method reference. */
    fun invokeSpecial(ref: MethodRef<*, *>) {
        assertInvokeType(InvokeType.SPECIAL, ref)
        stack.stackInvoke(ref)

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
        builder: ArgumentScope.() -> Unit = {},
    ) {
        new(owner)
        dup()
        val ref = findMethod<O, Unit>(ConstantDescs.INIT_NAME, owner, klassDescOf(), builder)
        invokeSpecial(ref)
    }

    /** Instantiates a reified type and calls its constructor. */
    inline fun <reified O : Any> instantiate(
        noinline builder: ArgumentScope.() -> Unit = {},
    ) {
        instantiate(klassDescOf<O>(), builder)
    }

    /**
     * Instantiates a value and stores it directly into the referenced field.
     *
     * @param fieldRef the field to initialize.
     * @param builder parameter metadata used to resolve the constructor.
     */
    inline fun <reified T : Any> instantiateField(
        fieldRef: FieldRef<*, T>,
        noinline builder: ArgumentScope.() -> Unit,
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
        noinline builder: ArgumentScope.() -> Unit = {},
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

    fun FieldRef<*, *>.load() {
        if (isStatic) getStatic(this)
        else {
            loadReceiver()
            getField(this)
        }
    }

    fun FieldRef<*, *>.store(body: CodeScope<O, R>.() -> Unit) {
        if (isStatic) putStatic(this)
        else {
            loadReceiver()
            body()
            putField(this)
        }
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

        stack.push(StackValue.Field(FieldRef(name, owner, type, flags = ACC_STATIC)))
    }

    /**
     * Emits `getstatic` using reified owner and field type parameters.
     *
     * @param name the field name.
     */
    inline fun <reified O : Any, reified T : Any> getStatic(name: String) =
        getStatic(name, klassDescOf<O>(), klassDescOf<T>())

    /** Emits `getstatic` from an existing field reference. */
    fun getStatic(ref: FieldRef<*, *>) {
        getStatic(ref.name, ref.owner, ref.type)
    }

    /** Emits a `getfield` instruction. */
    fun getField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        stack.pop(owner)
        raw { getfield(owner.classDesc, name, type.classDesc) }
        stack.push(StackValue.Field(FieldRef(name, owner, type, flags = 0)))
    }

    /** Loads either a static or instance field based on the reference metadata. */
    fun getField(ref: FieldRef<*, *>) {
        if (ref.isStatic) getStatic(ref)
        else getField(ref.name, ref.owner, ref.type)
    }

    /** Emits a `putstatic` instruction for the given field reference. */
    fun putStatic(ref: FieldRef<*, *>) {
        stack.pop(ref.type)
        raw { putstatic(ref.owner.classDesc, ref.name, ref.type.classDesc) }
    }

    /** Emits a `putfield` instruction. */
    fun putField(name: String, owner: KlassDesc<*>, type: KlassDesc<*>) {
        stack.pop(type)
        stack.pop(owner)
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

    fun ifEq(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { ifeq(label.getLabel()) }
    }

    fun ifNe(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { ifne(label.getLabel()) }
    }

    fun ifLt(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { iflt(label.getLabel()) }
    }

    fun ifLe(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { ifle(label.getLabel()) }
    }

    fun ifGt(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { ifgt(label.getLabel()) }
    }

    fun ifGe(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { ifge(label.getLabel()) }
    }

    fun ifICmpEq(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { if_icmpeq(label.getLabel()) }
    }

    fun ifICmpNe(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { if_icmpne(label.getLabel()) }
    }

    fun ifICmpLt(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { if_icmplt(label.getLabel()) }
    }

    fun ifICmpLe(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { if_icmple(label.getLabel()) }
    }

    fun ifICmpGt(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { if_icmpgt(label.getLabel()) }
    }

    fun ifICmpGe(label: LabelRef) {
        stack.pop(klassDescOf<Int>())
        stack.pop(klassDescOf<Int>())
        label.performIf(stack, this)

        raw { if_icmpge(label.getLabel()) }
    }

    private fun checkReferenceInStack(count: Int) {
        for (i in 0 until count) {
            val value = stack.pop()
            if (value !is StackValue.Reference)
                throw StackReferenceTypeExpectedError(value, this)
        }
    }

    fun ifACmpEq(label: LabelRef) {
        checkReferenceInStack(2)
        label.performIf(stack, this)

        raw { if_acmpeq(label.getLabel()) }
    }

    fun ifACmpNe(label: LabelRef) {
        checkReferenceInStack(2)

        label.performIf(stack, this)
        raw { if_acmpne(label.getLabel()) }
    }

    fun ifNull(label: LabelRef) {
        checkReferenceInStack(1)

        label.performIf(stack, this)
        raw { ifnull(label.getLabel()) }
    }

    fun ifNonNull(label: LabelRef) {
        checkReferenceInStack(1)

        label.performIf(stack, this)
        raw { ifnonnull(label.getLabel()) }
    }

    private fun parseIf(onRef: () -> Unit, onInt: () -> Unit) {
        val first = stack.peek()
        val second = stack.peek()

        if (!first.stackType.isEqualTo(second.stackType)) {
            throw StackTypeMismatchError(second.type, first.type, this)
        }

        if (first.stackType.isReference()) {
            onRef()
            return
        }

        if (first.stackType != StackType.INT) {
            throw StackTypeMismatchError(klassDescOf<Int>(), first.type, this)
        }

        onInt()
    }

    fun ifCmpEq(label: LabelRef) {
        parseIf(
            onRef = { ifACmpEq(label) },
            onInt = { ifICmpEq(label) }
        )
    }

    fun ifCmpNe(label: LabelRef) {
        parseIf(
            onRef = { ifACmpNe(label) },
            onInt = { ifICmpNe(label) }
        )
    }

    fun ifCmpLt(label: LabelRef) {
        parseIf(
            onRef = { throw UnsupportedOperationException("Cannot perform 'ifCmpLt' on reference types.") },
            onInt = { ifICmpLt(label) }
        )
    }

    fun ifCmpLe(label: LabelRef) {
        parseIf(
            onRef = { throw UnsupportedOperationException("Cannot perform 'ifCmpLe' on reference types.") },
            onInt = { ifICmpLe(label) }
        )
    }

    fun ifCmpGt(label: LabelRef) {
        parseIf(
            onRef = { throw UnsupportedOperationException("Cannot perform 'ifCmpGt' on reference types.") },
            onInt = { ifICmpGt(label) }
        )
    }

    fun ifCmpGe(label: LabelRef) {
        parseIf(
            onRef = { throw UnsupportedOperationException("Cannot perform 'ifCmpGe' on reference types.") },
            onInt = { ifICmpGe(label) }
        )
    }

    fun ifCmpNull(label: LabelRef) {
        parseIf(
            onRef = { ifNull(label) },
            onInt = { throw UnsupportedOperationException("Cannot perform 'ifCmpNull' on integer types.") }
        )
    }

    fun ifCmpNonNull(label: LabelRef) {
        parseIf(
            onRef = { ifNonNull(label) },
            onInt = { throw UnsupportedOperationException("Cannot perform 'ifCmpNonNull' on integer types.") }
        )
    }

    infix fun OrderedRef<*>.eq(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(this)
        loadRef(other)
        ifCmpEq(label)
        return label
    }

    infix fun OrderedRef<*>.eq(other: Int): LabelRef {
        val label by label()

        loadRef(this)
        if (other == 0) ifEq(label)
        else {
            ldc(other)
            ifCmpEq(label)
        }
        return label
    }

    infix fun Unit.eq(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(other)
        ifCmpEq(label)
        return label
    }

    infix fun Unit.eq(other: Int): LabelRef {
        val label by label()

        if (other == 0) ifEq(label)
        else {
            ldc(other)
            ifCmpEq(label)
        }
        return label
    }

    infix fun OrderedRef<*>.ne(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(this)
        loadRef(other)
        ifCmpNe(label)
        return label
    }

    infix fun OrderedRef<*>.ne(other: Int): LabelRef {
        val label by label()

        loadRef(this)
        if (other == 0) ifNe(label)
        else {
            ldc(other)
            ifCmpNe(label)
        }
        return label
    }

    infix fun Unit.ne(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(other)
        ifCmpNe(label)
        return label
    }

    infix fun Unit.ne(other: Int): LabelRef {
        val label by label()

        if (other == 0) ifNe(label)
        else {
            ldc(other)
            ifCmpNe(label)
        }
        return label
    }

    infix fun OrderedRef<*>.lt(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(this)
        loadRef(other)
        ifCmpLt(label)
        return label
    }

    infix fun OrderedRef<*>.lt(other: Int): LabelRef {
        val label by label()

        loadRef(this)
        if (other == 0) ifLt(label)
        else {
            ldc(other)
            ifCmpLt(label)
        }
        return label
    }

    infix fun Unit.lt(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(other)
        ifCmpLt(label)
        return label
    }

    infix fun Unit.lt(other: Int): LabelRef {
        val label by label()

        if (other == 0) ifLt(label)
        else {
            ldc(other)
            ifCmpLt(label)
        }
        return label
    }

    infix fun OrderedRef<*>.le(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(this)
        loadRef(other)
        ifCmpLe(label)
        return label
    }

    infix fun OrderedRef<*>.le(other: Int): LabelRef {
        val label by label()

        loadRef(this)
        if (other == 0) ifLe(label)
        else {
            ldc(other)
            ifCmpLe(label)
        }
        return label
    }

    infix fun Unit.le(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(other)
        ifCmpLe(label)
        return label
    }

    infix fun Unit.le(other: Int): LabelRef {
        val label by label()

        if (other == 0) ifLe(label)
        else {
            ldc(other)
            ifCmpLe(label)
        }
        return label
    }

    infix fun OrderedRef<*>.gt(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(this)
        loadRef(other)
        ifCmpGt(label)
        return label
    }

    infix fun OrderedRef<*>.gt(other: Int): LabelRef {
        val label by label()

        loadRef(this)
        if (other == 0) ifGt(label)
        else {
            ldc(other)
            ifCmpGt(label)
        }
        return label
    }

    infix fun Unit.gt(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(other)
        ifCmpGt(label)
        return label
    }

    infix fun Unit.gt(other: Int): LabelRef {
        val label by label()

        if (other == 0) ifGt(label)
        else {
            ldc(other)
            ifCmpGt(label)
        }
        return label
    }

    infix fun OrderedRef<*>.ge(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(this)
        loadRef(other)
        ifCmpGe(label)
        return label
    }

    infix fun OrderedRef<*>.ge(other: Int): LabelRef {
        val label by label()

        loadRef(this)
        if (other == 0) ifGe(label)
        else {
            ldc(other)
            ifCmpGe(label)
        }
        return label
    }

    infix fun Unit.ge(other: OrderedRef<*>): LabelRef {
        val label by label()

        loadRef(other)
        ifCmpGe(label)
        return label
    }

    infix fun Unit.ge(other: Int): LabelRef {
        val label by label()

        if (other == 0) ifGe(label)
        else {
            ldc(other)
            ifCmpGe(label)
        }
        return label
    }

    fun OrderedRef<*>.isNull(): LabelRef {
        val label by label()

        loadRef(this)
        ifCmpNull(label)
        return label
    }

    fun OrderedRef<*>.isNonNull(): LabelRef {
        val label by label()

        loadRef(this)
        ifCmpNonNull(label)
        return label
    }

    fun if_(cond: LabelRef, body: CodeScope<O, R>.() -> Unit) {
        val skipLabel by label()
        goto(skipLabel)
        cond.bind()
        body()
        skipLabel.bind()
    }

    fun while_(condition: CodeScope<O, R>.() -> LabelRef): WhileRef {
        val condLabel by label()
        val breakLabel by label()
        condLabel.bind()

        val bodyLabel = condition()
        goto(breakLabel)

        return WhileRef(condLabel, bodyLabel, breakLabel)
    }

    infix fun WhileRef.do_(body: CodeScope<O, R>.() -> Unit) {
        bodyLabel.bind()
        body()
        goto(condLabel)
        breakLabel.bind()
    }


    operator fun Int.rangeTo(other: OrderedRef<Int>) =
        CustomRange.IntToRefRange(this, other)

    infix fun Int.until(other: OrderedRef<Int>) =
        CustomRange.IntToRefRange(this, other, false)

    operator fun OrderedRef<Int>.rangeTo(other: Int) =
        CustomRange.RefToIntRange(this, other)

    infix fun OrderedRef<Int>.until(other: Int) =
        CustomRange.RefToIntRange(this, other, false)

    operator fun OrderedRef<Int>.rangeTo(other: OrderedRef<Int>) =
        CustomRange.RefToRefRange(this, other)

    infix fun OrderedRef<Int>.until(other: OrderedRef<Int>) =
        CustomRange.RefToRefRange(this, other, false)

    private fun CustomRange<*, *>.loadStart() {
        when (this) {
            is CustomRange.IntToRefRange -> ldc(start)
            is CustomRange.RefToIntRange, is CustomRange.RefToRefRange -> loadRef(start)
        }
    }

    private fun CustomRange<*, *>.loadEnd() {
        when (this) {
            is CustomRange.IntToRefRange, is CustomRange.RefToRefRange -> load(end)
            is CustomRange.RefToIntRange -> ldc(end)
        }
    }

    private fun ifCmpG(inclusive: Boolean, label: LabelRef) {
        if (!inclusive) ifCmpGe(label)
        else ifCmpGt(label)
    }

    infix fun OrderedRef<Int>.in_(range: CustomRange<*, *>): LabelRef {
        val label by label()
        loadRef(this)
        range.loadStart()
        ifCmpLt(label)
        loadRef(this)
        range.loadEnd()
        ifCmpG(range.endInclusive, label)
        return label
    }

    infix fun OrderedRef<Int>.in_(range: IntRange): LabelRef {
        val label by label()
        loadRef(this)
        ldc(range.first)
        ifCmpLt(label)
        loadRef(this)
        ldc(range.last)
        ifCmpGt(label)
        return label
    }

    fun for_(idx: LocalRef<Int>, condition: CodeScope<O, R>.(LocalRef<Int>) -> LabelRef): ForRef<O, R> {
        val condLabel by label()
        val bodyLabel by label()

        condLabel.bind()

        val breakLabel = condition(idx)
        goto(bodyLabel)
        return ForRef(condLabel, bodyLabel, breakLabel, idx)
    }

    fun for_(condition: CodeScope<O, R>.(LocalRef<Int>) -> LabelRef): ForRef<O, R> {
        val idx by local<Int>()
        idx set 0
        return for_(idx, condition)
    }

    private fun CustomRange<*, *>.initLocal(idx: LocalRef<Int>) {
        when (this) {
            is CustomRange.IntToRefRange -> idx set start
            is CustomRange.RefToIntRange, is CustomRange.RefToRefRange -> idx set load(start)
        }
    }

    private fun generateForIdx(): LocalRef<Int> {
        return local(Random.nextBytes(10).contentToString(), klassDescOf<Int>())
    }

    fun for_(range: CustomRange<*, *>): ForRef<O, R> {
        val idx = generateForIdx()
        range.initLocal(idx)

        return for_(idx) { idx in_ range }
    }

    fun for_(range: IntRange): ForRef<O, R> {
        val idx = generateForIdx()
        idx set range.first

        return for_(idx) { idx in_ range }
    }

    infix fun ForRef<O, R>.incrementor(incrementor: CodeScope<O, R>.() -> Unit): ForRef<O, R> {
        return copy(incrementor = incrementor)
    }

    infix fun ForRef<O, R>.do_(body: CodeScope<O, R>.(LocalRef<Int>) -> Unit) {
        bodyLabel.bind()
        body(idx)
        incrementor()
        goto(condLabel)
        breakLabel.bind()
    }

    //////////////// NEEDS REFACTORING

    /** Emits `pop`. */
    fun pop() {
        val top = stack.pop()

        if (top.category != 1)
            error("pop requires a category 1 value.")

        raw { pop() }
    }

    /** Emits `pop2`. */
    fun pop2() {
        val top = stack.pop()

        if (top.category == 2) {
            raw { pop2() }
            return
        }

        val second = stack.pop()

        if (second.category != 1)
            error("Invalid stack shape for pop2.")

        raw { pop2() }
    }

    /** Emits `dup`. */
    fun dup() {
        val top = stack.pop()

        if (top.category != 1)
            error("dup requires a category 1 value.")

        raw { dup() }

        stack.push(top)
        stack.push(top)
    }

    /** Emits `dup_x1`. */
    fun dupX1() {
        val v1 = stack.pop()
        val v2 = stack.pop()

        if (v1.category != 1 || v2.category != 1)
            error("dup_x1 requires two category 1 values.")

        raw { dup_x1() }

        stack.push(v1)
        stack.push(v2)
        stack.push(v1)
    }

    /** Emits `dup_x2`. */
    fun dupX2() {
        val v1 = stack.pop()

        if (v1.category != 1)
            error("dup_x2 requires a category 1 value on top.")

        val v2 = stack.pop()

        if (v2.category == 2) {
            raw { dup_x2() }

            stack.push(v1)
            stack.push(v2)
            stack.push(v1)
            return
        }

        val v3 = stack.pop()

        if (v2.category != 1 || v3.category != 1)
            error("Invalid stack shape for dup_x2.")

        raw { dup_x2() }

        stack.push(v1)
        stack.push(v3)
        stack.push(v2)
        stack.push(v1)
    }

    /** Emits `dup2`. */
    fun dup2() {
        val v1 = stack.pop()

        if (v1.category == 2) {
            raw { dup2() }

            stack.push(v1)
            stack.push(v1)
            return
        }

        val v2 = stack.pop()

        if (v2.category != 1)
            error("Invalid stack shape for dup2.")

        raw { dup2() }

        stack.push(v2)
        stack.push(v1)
        stack.push(v2)
        stack.push(v1)
    }

    /** Emits `dup2_x1`. */
    fun dup2X1() {
        val v1 = stack.pop()

        if (v1.category == 2) {
            val v2 = stack.pop()

            if (v2.category != 1)
                error("Invalid stack shape for dup2_x1.")

            raw { dup2_x1() }

            stack.push(v1)
            stack.push(v2)
            stack.push(v1)
            return
        }

        val v2 = stack.pop()
        val v3 = stack.pop()

        if (v2.category != 1 || v3.category != 1)
            error("Invalid stack shape for dup2_x1.")

        raw { dup2_x1() }

        stack.push(v2)
        stack.push(v1)
        stack.push(v3)
        stack.push(v2)
        stack.push(v1)
    }

    /** Emits `dup2_x2`. */
    fun dup2X2() {
        val v1 = stack.pop()

        when (v1.category) {
            2 -> {
                val v2 = stack.pop()

                if (v2.category == 2) {
                    raw { dup2_x2() }

                    stack.push(v1)
                    stack.push(v2)
                    stack.push(v1)
                } else {
                    val v3 = stack.pop()

                    if (v3.category != 1)
                        error("Invalid stack shape for dup2_x2.")

                    raw { dup2_x2() }

                    stack.push(v1)
                    stack.push(v3)
                    stack.push(v2)
                    stack.push(v1)
                }
            }

            1 -> {
                val v2 = stack.pop()

                if (v2.category != 1)
                    error("Invalid stack shape for dup2_x2.")

                val v3 = stack.pop()

                if (v3.category == 2) {
                    raw { dup2_x2() }

                    stack.push(v2)
                    stack.push(v1)
                    stack.push(v3)
                    stack.push(v2)
                    stack.push(v1)
                } else {
                    val v4 = stack.pop()

                    if (v3.category != 1 || v4.category != 1)
                        error("Invalid stack shape for dup2_x2.")

                    raw { dup2_x2() }

                    stack.push(v2)
                    stack.push(v1)
                    stack.push(v4)
                    stack.push(v3)
                    stack.push(v2)
                    stack.push(v1)
                }
            }
        }
    }

    /** Emits `swap`. */
    fun swap() {
        val v1 = stack.pop()
        val v2 = stack.pop()

        if (v1.category != 1 || v2.category != 1)
            error("swap requires two category 1 values.")

        raw { swap() }

        stack.push(v1)
        stack.push(v2)
    }

    /**
     * Finalizes the code block and writes the collected instructions to [db].
     *
     * @param db the code builder receiving the emitted instructions.
     * @throws StackNotEmptyError if there are unresolved values left over.
     * @throws NoReturnError if no return instruction was emitted.
     */
    fun build(db: CodeBuilder) {
        if (!hasReturn) throw NoReturnError(this)


        if (stack.isNotEmpty()) {
            throw StackNotEmptyError(this)
        }

        for (instruction in instructions) db.instruction()
    }
}