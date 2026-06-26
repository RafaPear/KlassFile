package pt.rafap.klassfile.models

import pt.rafap.klassfile.toModifiers
import java.lang.classfile.ClassBuilder
import java.lang.classfile.ClassFile.ACC_STATIC

/**
 * Describes a field declaration or reference within a generated class.
 */
data class FieldRef<O : Any, T : Any>(
    val name: String,
    override val owner: KlassDesc<O>,
    override val type: KlassDesc<T>,
    val flags: Int,
) : TypedRef<O, T> {
    /** True when the field is declared with the `static` modifier. */
    val isStatic: Boolean = flags and ACC_STATIC != 0

    /** Returns a human-readable field signature with modifiers and owner. */
    override fun toString() = buildString{
        val modifiers = toModifiers(flags)
        if (modifiers.isNotEmpty())
            modifiers.joinTo(this, " ", postfix = " ")
        append(owner.classDesc.displayName())
        append(".$name: ")
        append(type.classDesc.displayName())
    }

    companion object {

        /** Registers a field reference on a [ClassBuilder] using the stored metadata. */
        fun ClassBuilder.field(ref: FieldRef<*, *>): ClassBuilder = apply {
            withField(
                ref.name,
                ref.type.classDesc,
                ref.flags
            )
        }
    }
}
