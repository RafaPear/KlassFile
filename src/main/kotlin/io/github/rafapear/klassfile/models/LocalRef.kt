package io.github.rafapear.klassfile.models

/**
 * Describes a local variable reference in a method or code block.
 *
 * @param T The type of the local variable.
 * @property name The name of the local variable.
 * @property type The type of the local variable.
 * @property order The order of the local variable in the method's local variable table.
 */
open class LocalRef<T : Any>(
    val name: String,
    override val type: KlassDesc<T>,
    override val order: Int,
    var isInitialized: Boolean = false,
) : OrderedRef<T> {
    override val owner: KlassDesc<Any>
        get() = error("No owner available")

    fun <T: Any> withType(type: KlassDesc<T>): LocalRef<T> = LocalRef(name, type, order, isInitialized)

    /** Returns a compact `name: type` representation for diagnostics. */
    override fun toString() = "$name: $type"
}