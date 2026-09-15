package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.models.FieldRef
import io.github.rafapear.klassfile.models.FieldRef.Companion.field
import io.github.rafapear.klassfile.models.KlassDesc
import io.github.rafapear.klassfile.models.OwnerRef
import io.github.rafapear.klassfile.utils.EagerDelegate
import io.github.rafapear.klassfile.utils.FieldScopeDsl
import io.github.rafapear.klassfile.utils.klassDescOf
import java.lang.classfile.ClassBuilder

/**
 * Collects field declarations for a generated class.
 */
@Suppress("UNCHECKED_CAST")
@FieldScopeDsl
class FieldScope<O : Any>(val ownerRef: OwnerRef<O>) {
    private var fieldRefs = listOf<FieldRef<O, *>>()

    /** Builds a field reference and validates its access flags. */
    private fun <T : Any> buildFieldRef(
        name: String,
        type: KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit,
    ): FieldRef<O, T> {
        val flags = FlagsScope.FieldFlagsScope(name)
            .apply { access() }
            .build()

        return FieldRef(name, ownerRef.thisClass, type, flags)
    }

    /** Adds a field with an explicit name and type. */
    fun <T : Any> defineField(
        name: String,
        type: KlassDesc<T>,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): FieldRef<O, T> {
        val fieldRef = buildFieldRef(name, type, access)
        fieldRefs += fieldRef
        return fieldRef
    }

    /** Adds a field with an explicit name and type. */
    inline fun <reified T : Any> defineField(
        name: String,
        noinline access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): FieldRef<O, T> {
        return defineField(name, klassDescOf(), access)
    }

    /** Adds a delegated field whose name is inferred from the backing property. */
    inline fun <reified T : Any> field(
        noinline access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<O, T>> = EagerDelegate<FieldRef<O, T>> { _, property ->
        val name = property.name
        defineField(name, klassDescOf(), access)
    }

    /** Emits all collected fields into the provided class builder. */
    fun build(clb: ClassBuilder) {
        for (fieldRef in fieldRefs) clb.field(fieldRef)
    }
}