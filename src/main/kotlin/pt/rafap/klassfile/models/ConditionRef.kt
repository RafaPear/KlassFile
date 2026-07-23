package pt.rafap.klassfile.models

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.utils.StackSizeMismatch
import java.lang.classfile.Label

data class LabelRef(val name: String) {

    private var stackSizeCapture: Int? = null
    private var internalLabel: Label? = null
    private var isBound = false
    private var didSetUnreachable = false

    internal fun getLabel(): Label {
        return internalLabel ?: error("Label not initialized")
    }

    internal fun setBound() {
        isBound = true
    }

    internal fun setLabel(label: Label) {
        internalLabel = label
    }

    internal fun hasLabel(): Boolean {
        return internalLabel != null
    }

    internal fun setUnreachable(stack: Stack, scope: CodeScope<*, *>) {
        if (!stack.isUnreachable() && !isBound) {
            stack.setUnreachable()
            didSetUnreachable = true
        } else if (isBound) {
            val expectedSize = stackSizeCapture ?: return
            val actualSize = stack.size

            if (actualSize != expectedSize) {
                throw StackSizeMismatch(scope, actualSize, expectedSize)
            }
        }
    }

    internal fun clearUnreachable(stack: Stack) {
        stackSizeCapture = stack.size

        if (didSetUnreachable) {
            stack.clearUnreachable()
            didSetUnreachable = false
        }
    }

    internal fun isBound() = isBound
}

data class ConditionRef(
    val condition: CodeScope<*, *>.() -> Unit,
    //val label: LabelRef
)

data class ThenRef(
    val conditionRef: ConditionRef,
    //val label: LabelRef,
    val then: CodeScope<*, *>.() -> Unit,
)

data class OtherwiseRef(
    val thenRef: ThenRef,
    //val label: LabelRef,
    val otherwise: CodeScope<*, *>.() -> Unit,
)
