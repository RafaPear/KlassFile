package pt.rafap.klassfile.models

import java.lang.constant.ConstantDescs.INIT_NAME
import java.lang.reflect.Modifier

enum class InvokeType {
    STATIC,
    VIRTUAL,
    SPECIAL,
    INTERFACE;

    fun isStatic() = this == STATIC
    fun isVirtual() = this == VIRTUAL
    fun isInterface() = this == INTERFACE
    fun isSpecial() = this == SPECIAL

    companion object {
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