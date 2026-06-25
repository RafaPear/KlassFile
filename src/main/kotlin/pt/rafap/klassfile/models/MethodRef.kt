package pt.rafap.klassfile.models

import java.lang.classfile.CodeBuilder
import java.lang.constant.MethodTypeDesc
import java.lang.reflect.Modifier

data class MethodRef<O : Any, T : Any>(
    val name: String,
    override val owner: KlassDesc<O>,
    override val type: KlassDesc<T>,
    val params: List<ParamRef<*>>,
    val flags: Int,
    val invokeType: InvokeType,
    val code: ((CodeBuilder) -> Unit)? = null,
) : TypedRef<O, T> {
    val methodTypeDesc: MethodTypeDesc = MethodTypeDesc.of(
        type.classDesc,
        *params.map { it.type.classDesc }.toTypedArray()
    )

    private fun toModifiers(): List<String> = buildList {
        when {
            Modifier.isPublic(flags) -> add("public")
            Modifier.isProtected(flags) -> add("protected")
            Modifier.isPrivate(flags) -> add("private")
        }
        if (Modifier.isAbstract(flags)) add("abstract")
        if (Modifier.isStatic(flags)) add("static")
        if (Modifier.isFinal(flags)) add("final")
        if (Modifier.isNative(flags)) add("native")
    }

    override fun toString(): String = buildString {
        append(invokeType.name)
        append(" ")

        // Modifiers
        val modifiers = toModifiers()
        if (modifiers.isNotEmpty())
            modifiers.joinTo(this, " ", postfix = " ")

        // Owner
        append(owner.classDesc.displayName())

        // Method Name
        if (!invokeType.isSpecial()) append(".$name")

        // Parameters
        methodTypeDesc
            .parameterList()
            .joinTo(this, ", ", prefix = "(", postfix = ")") { it.displayName() }

        append(": ")
        append(methodTypeDesc.returnType().displayName())
    }
}
