package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.models.FieldRef.Companion.field
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.utils.EagerDelegate
import pt.rafap.klassfile.utils.FieldScopeDsl
import pt.rafap.klassfile.utils.toKlassDesc
import java.lang.classfile.ClassBuilder
import java.lang.constant.ConstantDescs.CD_void

@FieldScopeDsl
class FieldScope<O : Any>(val owner: KlassDesc<O>) {
    private var fieldRefs = listOf<FieldRef<O, *>>()

    private fun <T : Any> buildFieldRef(
        name: String,
        type: KlassDesc<T> = CD_void.toKlassDesc(),
        access: FlagsScope.FieldFlagsScope.() -> Unit,
    ): FieldRef<O, T> {
        val flags = FlagsScope.FieldFlagsScope(name)
            .apply { access() }
            .build()

        return FieldRef(name, owner, type, flags)
    }

    fun <T : Any> field(
        name: String,
        type: KlassDesc<T> = CD_void.toKlassDesc(),
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): FieldRef<O, T> {
        val fieldRef = buildFieldRef(name, type, access)
        fieldRefs += fieldRef
        return fieldRef
    }

    fun <T : Any> field(
        type: KlassDesc<T> = CD_void.toKlassDesc(),
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<O, T>> = EagerDelegate<FieldRef<O, T>> { _, property ->
        val name = property.name
        field(name, type, access)
    }

    fun build(clb: ClassBuilder) {
        for (fieldRef in fieldRefs) clb.field(fieldRef)
    }
}