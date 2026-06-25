package pt.rafap.klassfile.models

interface TypedRef<O : Any, T : Any> {
    val owner: KlassDesc<O>
    val type: KlassDesc<T>
}