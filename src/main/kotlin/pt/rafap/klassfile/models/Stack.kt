package pt.rafap.klassfile.models

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.models.StackType.Companion.stackType
import pt.rafap.klassfile.utils.StackTypeMismatchError
import pt.rafap.klassfile.utils.StackUnderflowError
import pt.rafap.klassfile.utils.StackVoidTypeError
import java.lang.constant.ConstantDescs

class Stack(private val codeScope: CodeScope<*, *>) {

    private var stackTypes = mutableListOf<StackValue>()
    private var invocationTrace = mutableListOf<MethodRef<*, *>>()
    private var isUnreachable = false
    private var markPos = 0

    val size: Int
        get() = stackTypes.size

    fun setUnreachable() {
        isUnreachable = true
    }

    fun clearUnreachable() {
        isUnreachable = false
    }

    fun isUnreachable() = isUnreachable

    fun mark() {
        markPos = stackTypes.size
    }

    fun resetToMark() {
        if (markPos < 0 || markPos > stackTypes.size) {
            throw IllegalStateException("Invalid mark position: $markPos")
        }

        stackTypes = stackTypes.subList(0, markPos).toMutableList()
    }

    /** Prints the tracked value trace for debugging. */
    fun printStackTypes() {
        if (stackTypes.isEmpty()) {
            println("Stack: (EMPTY)")
            return
        }

        println("Current Stack Types:")
        stackTypes.forEachIndexed { index, stackValue ->
            println("  [${index}]: $stackValue")
        }
    }

    /** Prints the tracked method-invocation trace for debugging. */
    fun printInvocationTrace() {
        if (invocationTrace.isEmpty()) {
            println("Invocation Trace: (EMPTY)")
            return
        }

        println("Current Invocation Trace:")
        invocationTrace.forEachIndexed { index, methodRef ->
            println("  [${index}]: $methodRef")
        }
    }

    /** Prints the tracked value and invocation traces for debugging. */
    fun print() {
        printStackTypes()
        printInvocationTrace()
    }

    /** Records a value in the internal value trace when applicable. */
    fun push(stackValue: StackValue) {
        if (isUnreachable) return
        if (stackValue.type.classDesc == ConstantDescs.CD_void) return
        stackTypes.add(stackValue)
    }

    fun push(ref: OrderedRef<*>) {
        when (ref) {
            is ParamRef<*> -> push(StackValue.Parameter(ref))
            is LocalRef<*> -> push(StackValue.Local(ref))
        }
    }

    /** Records a field value in the internal value trace. */
    fun push(ref: FieldRef<*, *>) {
        push(StackValue.Field(ref))
    }

    /** Records a method return value in the internal value trace. */
    fun push(ref: MethodRef<*, *>) {
        push(StackValue.ReturnValue(ref))
    }

    /**
     * Removes and returns the most recent tracked value.
     *
     * @return the most recent tracked value.
     * @throws StackUnderflowError when no values are available.
     */
    fun pop(): StackValue {
        return stackTypes.removeLastOrNull()
            ?: throw StackUnderflowError(codeScope, null)
    }

    /**
     * Removes and returns the most recent tracked value after a type check.
     *
     * @param expected the expected value type.
     * @return the removed value.
     */
    fun pop(expected: KlassDesc<*>): StackValue {
        if (expected.classDesc == ConstantDescs.CD_void)
            throw StackVoidTypeError(codeScope)
        expectTop(expected)
        return stackTypes.removeLast()
    }

    fun peek() = stackTypes.lastOrNull()
        ?: throw StackUnderflowError(codeScope, null)

    /** Applies invocation bookkeeping for a method call. */
    fun stackInvoke(ref: MethodRef<*, *>) {
        invocationTrace.add(ref)
        for (param in ref.params.asReversed()) {
            pop(param.type)
        }
        if (ref.invokeType != InvokeType.STATIC)
            pop(ref.owner)

        push(ref)
    }

    /** Validates the most recent tracked value against [expected]. */
    private fun expectTop(expected: KlassDesc<*>) {
        val actual = stackTypes.lastOrNull()
            ?: throw StackUnderflowError(codeScope, expected)

        if (actual.stackType.isEqualTo(expected.stackType)) return

        if (actual.stackType.isPrimitive() != expected.stackType.isPrimitive()) {
            throw StackTypeMismatchError(expected, actual.type, codeScope)
        }

        if (!expected.isAssignableFrom(actual.type)) {
            throw StackTypeMismatchError(expected, actual.type, codeScope)
        }
    }

    fun isEmpty() = stackTypes.isEmpty()
    fun isNotEmpty() = stackTypes.isNotEmpty()
}