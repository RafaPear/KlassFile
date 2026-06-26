package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.klassDescOf

/**
 * Represents a simulated operand-stack value tracked by [pt.rafap.klassfile.builders.CodeScope].
 *
 * The hierarchy is used to validate stack shape and value categories while bytecode is being emitted.
 */
sealed class StackValue(
    open val type: KlassDesc<*>,
    val category: Int = when (type.kClass) {
        Long::class, Double::class -> 2
        else -> 1
    },
) {

    /** A constant literal value pushed from the constant pool. */
    data class Constant(
        override val type: KlassDesc<*>,
        val value: Any?,
    ) : StackValue(type) {
        /** Returns a debug representation including value and type. */
        override fun toString() = "$value: $type"
    }

    /** A value loaded from a method parameter. */
    data class Parameter(
        val ref: ParamRef<*>,
    ) : StackValue(ref.type) {
        /** Returns a debug representation of the referenced parameter. */
        override fun toString() = "$ref"
    }

    /** A value stored in a local slot. */
    data class Local(
        val ref: ParamRef<*>,
    ) : StackValue(ref.type) {
        /** Returns a debug representation of the referenced local slot. */
        override fun toString() = "$ref (local)"
    }

    /** A field value read from the current instance or a static holder. */
    data class Field(
        val ref: FieldRef<*, *>,
    ) : StackValue(ref.type) {
        /** Returns a debug representation of the referenced field value. */
        override fun toString() = "${ref.type.classDesc.displayName()} (field ${ref.name})"
    }

    /** A return value produced by a method invocation. */
    data class ReturnValue(
        val ref: MethodRef<*, *>,
    ) : StackValue(ref.type) {
        /** Returns a debug representation of the method return value. */
        override fun toString() = "${ref.type.classDesc.displayName()} (return value of ${ref.name})"
    }

    /** A freshly created object that has not yet been initialized. */
    data class NewObject(
        override val type: KlassDesc<*>,
    ) : StackValue(type) {
        /** Returns a debug representation of a newly created object value. */
        override fun toString() = "${type.classDesc.displayName()} (new)"
    }

    /** The explicit `null` reference value. */
    data class Null(
        val dummy: Unit = Unit,
    ) : StackValue(klassDescOf<Any>()){
        /** Returns a debug representation of the null literal. */
        override fun toString() = "null"
    }

    /** A placeholder value used when the precise type is not known yet. */
    data class Unknown(
        override val type: KlassDesc<*>,
    ) : StackValue(type) {
        /** Returns a debug representation of an unresolved value type. */
        override fun toString() = "$type (unknown)"
    }
}