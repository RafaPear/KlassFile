package pt.rafap.klassfile.models

interface OrderedRef<T: Any>: TypedRef<Any, T> {
    val order: Int
}