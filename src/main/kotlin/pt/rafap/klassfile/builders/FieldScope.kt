package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.models.FieldRef.Companion.field
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.utils.EagerDelegate
import pt.rafap.klassfile.utils.FieldScopeDsl
import java.lang.classfile.ClassBuilder

/**
 * Collects field declarations for a generated class.
 */
@Suppress("UNCHECKED_CAST")
@FieldScopeDsl
class FieldScope<O : Any>(val owner: KlassDesc<O>) {
    private var fieldRefs = listOf<FieldRef<O, *>>()

    /** Builds a field reference and validates its access flags. */
    private fun <T : Any> buildFieldRef(
        name: String,
        type: KlassDesc<T> = pt.rafap.klassfile.utils.klassDescOf<Unit>() as KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit,
    ): FieldRef<O, T> {
        val flags = FlagsScope.FieldFlagsScope(name)
            .apply { access() }
            .build()

        return FieldRef(name, owner, type, flags)
    }

    /** Adds a field with an explicit name and type. */
    fun <T : Any> field(
        name: String,
        type: KlassDesc<T> = pt.rafap.klassfile.utils.klassDescOf<Unit>() as KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): FieldRef<O, T> {
        val fieldRef = buildFieldRef(name, type, access)
        fieldRefs += fieldRef
        return fieldRef
    }

    /** Adds a delegated field whose name is inferred from the backing property. */
    fun <T : Any> field(
        type: KlassDesc<T> = pt.rafap.klassfile.utils.klassDescOf<Unit>() as KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<O, T>> = EagerDelegate<FieldRef<O, T>> { _, property ->
        val name = property.name
        field(name, type, access)
    }

    /** Emits all collected fields into the provided class builder. */
    fun build(clb: ClassBuilder) {
        for (fieldRef in fieldRefs) clb.field(fieldRef)
    }
}