package pt.rafap.klassfile.models

/**
 * Describes a parameter in declaration order.
 *
 * Parameters are identified by their name, resolved type, and slot order within
 * the method signature. Receiver parameters are represented by [ReceiverRef].
 */
open class ParamRef<T : Any>(
    val name: String,
    val type: KlassDesc<T>,
    val order: Int,
) {
    /**
     * Marker reference used for the implicit `this` parameter of instance members.
     */
    class ReceiverRef<T : Any>(type: KlassDesc<T>) : ParamRef<T>("this", type, 0)

    override fun toString() = "$name: $type"
}