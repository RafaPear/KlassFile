package pt.rafap.klassfile.models

/**
 * Shared contract for references that have both an owner type and a value type.
 *
 * Field, method, and parameter references all expose the class that owns the
 * member together with the Kotlin/bytecode type they describe.
 */
interface OwnedRef<O : Any, T : Any> : TypedRef<O, T> {
    val ownerRef: OwnerRef<O>
    override val owner: KlassDesc<O>
        get() = ownerRef.thisClass
}