package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.models.FieldRef.Companion.field
import pt.rafap.klassfile.utils.EagerDelegate
import pt.rafap.klassfile.utils.FieldScopeDsl
import java.lang.classfile.ClassBuilder
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.CD_void

@FieldScopeDsl
class FieldScope(val thisClassDesc: ClassDesc) {
    private var fieldRefs = listOf<FieldRef<*>>()

    private fun <T> buildFieldRef(
        name: String,
        type: ClassDesc = CD_void,
        access: FlagsScope.FieldFlagsScope.() -> Unit
    ): FieldRef<T> {
        val flags = FlagsScope.FieldFlagsScope(thisClassDesc.displayName())
            .apply { access() }
            .build()

        return FieldRef(thisClassDesc, name, type, flags)
    }

    fun <T> field(
        name: String,
        type: ClassDesc = CD_void,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): FieldRef<T> {
        val fieldRef = buildFieldRef<T>(name, type, access)
        fieldRefs += fieldRef
        return fieldRef
    }

    fun <T> field(
        type: ClassDesc = CD_void,
        access: FlagsScope.FieldFlagsScope.() -> Unit = { private() },
    ): EagerDelegate<FieldRef<T>> = EagerDelegate<FieldRef<T>> { _, property ->
        val name = property.name
        field(name, type, access)
    }

    fun build(clb: ClassBuilder) {
        for (fieldRef in fieldRefs) clb.field(fieldRef)
    }
}