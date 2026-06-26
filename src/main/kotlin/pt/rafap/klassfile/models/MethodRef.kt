package pt.rafap.klassfile.models

import pt.rafap.klassfile.toModifiers
import java.lang.classfile.CodeBuilder
import java.lang.constant.MethodTypeDesc

/**
 * Describes a method or constructor that can be emitted or invoked by the DSL.
 *
 * The reference stores the member name, owner, return type, parameters, flags,
 * invocation kind, and optional code generator used when the method is built.
 */
data class MethodRef<O : Any, T : Any>(
    val name: String,
    override val owner: KlassDesc<O>,
    override val type: KlassDesc<T>,
    val params: List<ParamRef<*>>,
    val flags: Int,
    val invokeType: InvokeType,
    val code: ((CodeBuilder) -> Unit)? = null,
) : TypedRef<O, T> {
    /** JVM method descriptor built from the declared return type and parameters. */
    val methodTypeDesc: MethodTypeDesc = MethodTypeDesc.of(
        type.classDesc,
        *params.map { it.type.classDesc }.toTypedArray()
    )

    /**
     * Formats the reference as a human-readable signature including modifiers,
     * owner, invocation kind, parameters, and return type.
     */
    override fun toString(): String = buildString {
        append(invokeType.name)
        append(" ")

        // Modifiers
        val modifiers = toModifiers(flags)
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
