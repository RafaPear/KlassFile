package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.ParamRef
import pt.rafap.klassfile.utils.klassDescOf

/**
 * Collects method parameters in declaration order for later overload resolution.
 */
@Suppress("UNUSED")
class ArgumentScope {
    private var args: MutableList<ParamRef<*>> = mutableListOf()

    /** Adds a parameter with an explicit type descriptor. */
    fun arg(type: KlassDesc<*>): ParamRef<*> {
        return ParamRef("", type, 0).also { args.add(it) }
    }

    /** Adds a parameter using a reified Kotlin type. */
    inline fun <reified T : Any> arg() = arg(klassDescOf<T>())

    /** Returns the collected parameters as an immutable list. */
    fun build(): List<ParamRef<*>> = args.toList()
}