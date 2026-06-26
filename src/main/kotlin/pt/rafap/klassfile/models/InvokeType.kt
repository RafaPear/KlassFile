package pt.rafap.klassfile.models

import java.lang.constant.ConstantDescs.INIT_NAME
import java.lang.reflect.Modifier

/**
 * Describes the opcode family used to invoke a method.
 */
@Suppress("unused")
enum class InvokeType {
    /** An invocation that targets a static member. */
    STATIC,
    /** A normal virtual dispatch. */
    VIRTUAL,
    /** A special invocation such as a constructor or private member call. */
    SPECIAL,
    /** An interface dispatch. */
    INTERFACE;

    /** Returns `true` when this invocation uses `invokestatic`. */
    fun isStatic() = this == STATIC
    /** Returns `true` when this invocation uses `invokevirtual`. */
    fun isVirtual() = this == VIRTUAL
    /** Returns `true` when this invocation uses `invokeinterface`. */
    fun isInterface() = this == INTERFACE
    /** Returns `true` when this invocation uses `invokespecial`. */
    fun isSpecial() = this == SPECIAL

    companion object {
        /**
         * Derives the most likely invocation type from a member name and its JVM flags.
         *
         * Constructors are always treated as special, static members as static,
         * abstract members as interface calls, and everything else as virtual.
         */
        fun fromFlags(name: String, flags: Int): InvokeType {
            return when {
                name == INIT_NAME -> SPECIAL
                Modifier.isStatic(flags) -> STATIC // ACC_STATIC
                Modifier.isAbstract(flags) -> INTERFACE // ACC_ABSTRACT
                else -> VIRTUAL
            }
        }
    }
}