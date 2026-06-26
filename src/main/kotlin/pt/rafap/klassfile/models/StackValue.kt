package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.klassDescOf

sealed class StackValue(
    open val type: KlassDesc<*>,
    val category: Int = when (type.kClass) {
        Long::class, Double::class -> 2
        else -> 1
    },
) {

    data class Constant(
        override val type: KlassDesc<*>,
        val value: Any?,
    ) : StackValue(type) {
        override fun toString() = "$value: $type"
    }

    data class Parameter(
        val ref: ParamRef<*>,
    ) : StackValue(ref.type) {
        override fun toString() = "$ref"
    }

    data class Local(
        val ref: ParamRef<*>,
    ) : StackValue(ref.type) {
        override fun toString() = "$ref (local)"
    }

    data class Field(
        val ref: FieldRef<*, *>,
    ) : StackValue(ref.type) {
        override fun toString() = "${ref.type.classDesc.displayName()} (field ${ref.name})"
    }

    data class ReturnValue(
        val ref: MethodRef<*, *>,
    ) : StackValue(ref.type) {
        override fun toString() = "${ref.type.classDesc.displayName()} (return value of ${ref.name})"
    }

    data class NewObject(
        override val type: KlassDesc<*>,
    ) : StackValue(type) {
        override fun toString() = "${type.classDesc.displayName()} (new)"
    }

    data class Null(
        val dummy: Unit = Unit,
    ) : StackValue(klassDescOf<Any>()){
        override fun toString() = "null"
    }

    data class Unknown(
        override val type: KlassDesc<*>,
    ) : StackValue(type) {
        override fun toString() = "$type (unknown)"
    }
}