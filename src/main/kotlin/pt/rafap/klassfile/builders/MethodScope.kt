package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.*
import pt.rafap.klassfile.utils.*
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
class MethodScope<O : Any, R : Any>(
    val name: String,
    override val owner: KlassDesc<O>,
    override val type: KlassDesc<R>,
    private val invokeType: InvokeType,
    private val hasThis: Boolean = true,
    body: MethodScope<O, R>.() -> Unit = {},
) : TypedRef<O, R> {

    private val params = mutableListOf<ParamRef<*>>()
    private var paramCounter = if (hasThis) 1 else 0
    val receiver: ParamRef.ReceiverRef<O>
        get() = if (hasThis) ParamRef.ReceiverRef(owner) else throw NoParamFoundError("this")

    private var codeScope: CodeScope<O, R>? = null
    private val flagsScope = FlagsScope.MethodFlagsScope(generateSignatureName())

    private var canDefineParams = true

    init {
        body()
    }

    /** Builds a signature string used for flag validation and error messages. */
    private fun generateSignatureName() = buildString {
        if (invokeType.isSpecial()) append(owner.classDesc.displayName())
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
    fun <R : Any> parameter(name: String, type: KlassDesc<R>): ParamRef<R> = generateParam(name, type)

    /** Adds a parameter using a reified Kotlin type. */
    inline fun <reified R : Any> parameter(name: String): ParamRef<R> = parameter(name, klassDescOf<R>())

    /** Adds a lazily named parameter based on the backing property name. */
    inline fun <reified R : Any> parameter(): EagerDelegate<ParamRef<R>> = EagerDelegate { _, property ->
        parameter(property.name)
    }

    /** Adds a lazily named parameter with an explicit type. */
    fun <R : Any> parameter(type: KlassDesc<R>): EagerDelegate<ParamRef<R>> = EagerDelegate { _, property ->
        parameter(property.name, type)
    }

    /** Starts or continues the code-emission block for this method. */
    fun code(body: CodeScope<O, R>.() -> Unit) {
        canDefineParams = false
        val newParams = buildList {
            if (hasThis) add(receiver)
            addAll(params)
        }

        if (codeScope == null) codeScope = CodeScope(name, type, owner, newParams)
        codeScope?.body()
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
            owner = owner,
            type = type,
            params = params,
            flags = flags,
            invokeType = invokeType,
            code = code
        )
    }
}
