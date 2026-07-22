package pt.rafap.klassfile.models

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.utils.StackTypeMismatchError
import pt.rafap.klassfile.utils.StackUnderflowError
import pt.rafap.klassfile.utils.StackVoidTypeError
import java.lang.constant.ConstantDescs

class Stack(private val codeScope: CodeScope<*, *>) {

    private var stackTypes = mutableListOf<StackValue>()
    private var invocationTrace = mutableListOf<MethodRef<*, *>>()

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
    fun pushStack(stackValue: StackValue) {
        if (stackValue.type.classDesc == ConstantDescs.CD_void) return
        stackTypes.add(stackValue)
    }

    /** Records a parameter value in the internal value trace. */
    fun pushStack(ref: ParamRef<*>) {
        pushStack(StackValue.Parameter(ref))
    }

    /** Records a field value in the internal value trace. */
    fun pushStack(ref: FieldRef<*, *>) {
        pushStack(StackValue.Field(ref))
    }

    /** Records a method return value in the internal value trace. */
    fun pushStack(ref: MethodRef<*, *>) {
        pushStack(StackValue.ReturnValue(ref))
    }

    /**
     * Removes and returns the most recent tracked value.
     *
     * @return the most recent tracked value.
     * @throws StackUnderflowError when no values are available.
     */
    fun popStack(): StackValue {
        return stackTypes.removeLastOrNull() ?: throw StackUnderflowError(codeScope, null)
    }

    /**
     * Removes and returns the most recent tracked value after a type check.
     *
     * @param expected the expected value type.
     * @return the removed value.
     */
    fun popStack(expected: KlassDesc<*>): StackValue {
        if (expected.classDesc == ConstantDescs.CD_void)
            throw StackVoidTypeError(codeScope)
        expectTop(StackValue.NewObject(expected))
        return stackTypes.removeLast()
    }

    /** Applies invocation bookkeeping for a method call. */
    fun stackInvoke(ref: MethodRef<*, *>) {
        invocationTrace.add(ref)
        for (param in ref.params.asReversed()) {
            popStack(param.type)
        }
        if (ref.invokeType != InvokeType.STATIC)
            popStack(ref.owner)

        pushStack(ref)
    }

    /** Validates the most recent tracked value against [expected]. */
    private fun expectTop(expected: StackValue) {
        val actual = stackTypes.lastOrNull()
            ?: throw StackUnderflowError(codeScope, expected)

        if (!expected.type.isAssignableFrom(actual.type)) {
            throw StackTypeMismatchError(expected, actual, codeScope)
        }
    }

    fun isEmpty() = stackTypes.isEmpty()
    fun isNotEmpty() = stackTypes.isNotEmpty()
}