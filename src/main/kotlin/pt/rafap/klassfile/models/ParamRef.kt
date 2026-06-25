package pt.rafap.klassfile.models

open class ParamRef<T : Any>(
    val name: String,
    val type: KlassDesc<T>,
    val order: Int,
) {
    class ReceiverRef<T : Any>(type: KlassDesc<T>) : ParamRef<T>("this", type, 0)

}