package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.klassDescOf

/**
 * Represents a simulated operand-stack value tracked by [pt.rafap.klassfile.builders.CodeScope].
 *
 * The hierarchy is used to validate stack shape and value categories while bytecode is being emitted.
 */
sealed class StackValue(open val type: KlassDesc<*>) {

    val category: Int
        get() = when (type.kClass) {
            Long::class, Double::class -> 2
            else -> 1
        }

    fun withType(type: KlassDesc<*>): StackValue {
        return when (this) {
            is Constant -> Constant(type, value)
            is KnownType -> KnownType(type)
            is Parameter -> Parameter(ref)
            is Local -> Local(ref)
            is Field -> Field(ref)
            is ReturnValue -> ReturnValue(ref)
            is Reference.NewObject -> Reference.NewObject(type)
            is Reference.NewArrayObject -> Reference.NewArrayObject(type as KlassDesc.ArrayKlassDesc<*>)
            is Reference.Null -> Reference.Null()
        }
    }

    /** A constant literal value pushed from the constant pool. */
    data class Constant(
        override val type: KlassDesc<*>,
        val value: Any?,
    ) : StackValue(type) {
        /** Returns a debug representation including value and type. */
        override fun toString() = "$value: $type"
    }

    /** A known value with a specific type, but no associated constant or reference. */
    data class KnownType(
        override val type: KlassDesc<*>,
    ) : StackValue(type) {
        /** Returns a debug representation of the known value type. */
        override fun toString() = "$type"
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
        val ref: LocalRef<*>,
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

    sealed class Reference(override val type: KlassDesc<*>) : StackValue(type) {

        /** A freshly created object that has not yet been initialized. */
        data class NewObject(
            override val type: KlassDesc<*>,
        ) : Reference(type) {
            /** Returns a debug representation of a newly created object value. */
            override fun toString() = "${type.classDesc.displayName()} (new)"
        }

        data class NewArrayObject(
            override val type: KlassDesc.ArrayKlassDesc<*>,
        ) : Reference(type) {
            constructor(elementType: KlassDesc<*>) : this(elementType.array())

            /** Returns a debug representation of a newly created array object value. */
            override fun toString() = "${type.classDesc.displayName()} (new array)"
        }

        /** The explicit `null` reference value. */
        data class Null(
            val dummy: Unit = Unit,
        ) : Reference(klassDescOf<Any>()) {
            /** Returns a debug representation of the null literal. */
            override fun toString() = "null"
        }
    }
}

