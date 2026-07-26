package pt.rafap.klassfile.models

interface OrderedRef<T: Any>: TypedRef<Any, T>, Comparable<OrderedRef<T>> {
    val order: Int

    override fun compareTo(other: OrderedRef<T>): Int {
        return 0
    }
}