package pt.rafap.klassfile.models

/**
 * Shared contract for references that have both an owner type and a value type.
 *
 * Field, method, and parameter references all expose the class that owns the
 * member together with the Kotlin/bytecode type they describe.
 */
interface TypedRef<O : Any, T : Any> {
    val owner: KlassDesc<O>
    val type: KlassDesc<T>
}