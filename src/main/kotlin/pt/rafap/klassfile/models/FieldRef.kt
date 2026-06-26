package pt.rafap.klassfile.models

import pt.rafap.klassfile.toModifiers
import java.lang.classfile.ClassBuilder
import java.lang.classfile.ClassFile.ACC_STATIC
import java.lang.classfile.CodeBuilder
import java.lang.classfile.constantpool.FieldRefEntry

data class FieldRef<O : Any, T : Any>(
    val name: String,
    override val owner: KlassDesc<O>,
    override val type: KlassDesc<T>,
    val flags: Int,
) : TypedRef<O, T> {
    val isStatic: Boolean = flags and ACC_STATIC != 0

    override fun toString() = buildString{
        val modifiers = toModifiers(flags)
        if (modifiers.isNotEmpty())
            modifiers.joinTo(this, " ", postfix = " ")
        append(owner.classDesc.displayName())
        append(".$name: ")
        append(type.classDesc.displayName())
    }

    companion object {
        fun CodeBuilder.toFieldRefEntry(ref: FieldRef<*, *>): FieldRefEntry =
            constantPool().fieldRefEntry(
                ref.owner.classDesc,
                ref.name,
                ref.type.classDesc
            )

        fun ClassBuilder.field(ref: FieldRef<*, *>): ClassBuilder = apply {
            withField(
                ref.name,
                ref.type.classDesc,
                ref.flags
            )
        }
    }
}
