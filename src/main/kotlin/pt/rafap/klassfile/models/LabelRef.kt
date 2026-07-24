package pt.rafap.klassfile.models

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.utils.StackSizeMismatch
import java.lang.classfile.Label

data class LabelRef(val name: String) {

    private var stackSizeCapture: Int? = null
    private var internalLabel: Label? = null
    private var isBound = false
    var boundIdx = -1
        private set
    private var didSetUnreachable = false
    private var isIf = false

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

    internal fun performGoto(stack: Stack, scope: CodeScope<*, *>) {
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

    internal fun performIf(stack: Stack, scope: CodeScope<*, *>) {
        isIf = true
        if (!stack.isUnreachable() && !isBound) {
            stack.mark()
        } else if (isBound) {
            val expectedSize = stackSizeCapture ?: return
            val actualSize = stack.size

            if (actualSize != expectedSize) {
                throw StackSizeMismatch(scope, actualSize, expectedSize)
            }
        }
    }

    internal fun performBiding(stack: Stack, idx: Int) {
        boundIdx = idx
        stackSizeCapture = stack.size

        if (isIf) {
            stack.resetToMark()
            stack.clearUnreachable()
            return
        }

        if (didSetUnreachable) {
            stack.clearUnreachable()
            didSetUnreachable = false
        }
    }

    internal fun isBound() = isBound
}