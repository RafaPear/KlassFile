package pt.rafap.klassfile.models

/**
 * Describes a parameter in declaration order.
 *
 * Parameters are identified by their name, resolved type, and slot order within
 * the method signature. Receiver parameters are represented by [ReceiverRef].
 */
open class ParamRef<T : Any>(
    val name: String,
    override val type: KlassDesc<T>,
    val order: Int,
): TypedRef<Any, T> {
    override val owner: KlassDesc<Any>
        get() = error("No owner available")
    /**
     * Marker reference used for the implicit `this` parameter of instance members.
     */
    class ReceiverRef<T : Any>(type: KlassDesc<T>) : ParamRef<T>("this", type, 0)

    /** Returns a compact `name: type` representation for diagnostics. */
    override fun toString() = "$name: $type"
}