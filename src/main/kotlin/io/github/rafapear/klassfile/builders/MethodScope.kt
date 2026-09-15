package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.models.*
import io.github.rafapear.klassfile.utils.*
import java.lang.classfile.CodeBuilder
import java.lang.constant.ConstantDescs.INIT_NAME
import java.lang.reflect.Modifier


/**
 * Builds a single method or constructor declaration.
 *
 * The scope tracks parameters, access flags, and an optional code block that is
 * materialized into a [MethodRef] when [build] is called.
 */
@MethodScopeDsl
@KlassFileApi
@Suppress("UNUSED")
class MethodScope<O : Any, R : Any>(
    val name: String,
    override val ownerRef: OwnerRef<O>,
    override val type: KlassDesc<R>,
    private val invokeType: InvokeType,
    private val hasThis: Boolean = true,
    body: MethodScope<O, R>.() -> Unit = {},
) : OwnedRef<O, R> {

    private val params = mutableListOf<ParamRef<*>>()
    private var paramCounter = if (hasThis) 1 else 0
    val receiver: ParamRef.ReceiverRef<O>
        get() = if (hasThis) ParamRef.ReceiverRef(ownerRef.thisClass) else throw NoParamFoundError("this")

    private var codeScope: CodeScope<O, R>? = null
    private val flagsScope = FlagsScope.MethodFlagsScope(generateSignatureName())

    var canDefineParams = true
        private set

    init {
        body()
    }

    /** Builds a signature string used for flag validation and error messages. */
    private fun generateSignatureName() = buildString {
        if (invokeType.isSpecial()) append(ownerRef.thisClass.classDesc.displayName())
        else append(name)

        append("(")
        params.forEachIndexed { index, ref ->
            append("${ref.name}: ${ref.type.classDesc.displayName()}")
            if (index < params.size - 1) append(", ")
        }
        append("): ")
        append(type.classDesc.displayName())
    }

    /** Adds a new parameter to the method signature. */
    private fun <R : Any> generateParam(name: String, type: KlassDesc<R>): ParamRef<R> {
        if (!canDefineParams) throw ParamDefinitionInCodeError(name)
        return ParamRef(name, type, paramCounter++).also { params.add(it) }
    }

    /** Returns a delegate for the implicit receiver parameter. */
    fun receiver(): EagerDelegate<ParamRef.ReceiverRef<O>> = EagerDelegate { _, _ -> receiver }

    /** Adds a parameter with an explicit type descriptor. */
    fun <R : Any> defineParam(name: String, type: KlassDesc<R>): ParamRef<R> = generateParam(name, type)

    /** Adds a parameter using a reified Kotlin type. */
    inline fun <reified R : Any> defineParam(name: String): ParamRef<R> = defineParam(name, klassDescOf<R>())

    /** Adds a lazily named parameter based on the backing property name. */
    inline fun <reified R : Any> param(): EagerDelegate<ParamRef<R>> {
        if (!canDefineParams) throw ParamDefinitionInCodeError(name)
        return EagerDelegate { _, property ->
            defineParam(property.name)
        }
    }

    /** Adds a lazily named parameter with an explicit type. */
    fun <R : Any> param(type: KlassDesc<R>): EagerDelegate<ParamRef<R>> = EagerDelegate { _, property ->
        defineParam(property.name, type)
    }

    private var isInCode = false

    /** Starts or continues the code-emission block for this method. */
    fun code(body: CodeScope<O, R>.() -> Unit) {
        if (isInCode) throw NestedCodeScopes(this)

        isInCode = true
        canDefineParams = false
        val newParams = buildList {
            if (hasThis) add(receiver)
            addAll(params)
        }

        if (codeScope == null) codeScope = CodeScope(name, type, ownerRef, newParams)
        codeScope?.body()
        isInCode = false
    }

    /** Configures method access flags. */
    fun access(body: FlagsScope.MethodFlagsScope.() -> Unit) {
        flagsScope.body()
    }

    /** Builds the final [MethodRef] for this method scope. */
    fun build(): MethodRef<O, R> {
        val flags = flagsScope.build()

        val invokeType = when {
            flags and Modifier.STATIC != 0 -> InvokeType.STATIC
            name == INIT_NAME -> InvokeType.SPECIAL
            else -> InvokeType.VIRTUAL
        }

        val code: ((CodeBuilder) -> Unit)? = codeScope?.let {
            { cb -> it.build(cb) }
        }

        return MethodRef(
            name = name,
            owner = ownerRef.thisClass,
            type = type,
            params = params,
            flags = flags,
            invokeType = invokeType,
            code = code
        )
    }
}
