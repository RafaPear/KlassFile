package pt.rafap.klassfile.builders

import pt.rafap.klassfile.NumberCenas
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.KlassDesc.Companion.withType
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.utils.*
import java.lang.classfile.CodeBuilder
import java.lang.constant.ConstantDescs.*
import java.lang.constant.MethodTypeDesc

open class ParamRef<T : Any>(
    val name: String,
    val type: KlassDesc<T>,
    val order: Int,
)

class ReceiverRef<T : Any>(type: KlassDesc<T>) : ParamRef<T>("this", type, 0)


@CodeScopeDsl
class CodeScope<T : Any>(
    val returnType: KlassDesc<T>,
    val className: String,
    val params: List<ParamRef<*>>,
) {
    private var instructions = mutableListOf<CodeBuilder.() -> Unit>()

    private var isInsideRawBlock = false
    fun raw(block: CodeBuilder.() -> Unit) {
        if (isInsideRawBlock) throw NestedRawBlockError()
        isInsideRawBlock = true
        instructions.add(block)
        isInsideRawBlock = false
    }

    fun ret() = raw {
        when (returnType.classDesc) {
            CD_void -> return_()
            CD_int -> ireturn()
            CD_long -> lreturn()
            CD_float -> freturn()
            CD_double -> dreturn()
            else -> areturn()
        }
    }

    private fun getParam(slot: Int) = params.firstOrNull { it.order == slot }
        ?: throw InvalidSlotIndexError(slot)

    fun load(slot: Int) = raw {
        val param = getParam(slot)

        when (param.type.classDesc) {
            CD_int -> iload(slot)
            CD_long -> lload(slot)
            CD_float -> fload(slot)
            CD_double -> dload(slot)
            else -> aload(slot)
        }
    }

    fun store(slot: Int) = raw {
        val param = getParam(slot)

        when (param.type.classDesc) {
            CD_int -> istore(slot)
            CD_long -> lstore(slot)
            CD_float -> fstore(slot)
            CD_double -> dstore(slot)
            else -> astore(slot)
        }
    }

    inline fun <reified T : ParamRef<*>> load(ref: T) = load(ref.order)


    inline fun <reified T : ParamRef<*>> store(ref: T) = store(ref.order)

    operator fun <T : Any> ParamRef<T>.plus(ref: ParamRef<T>) = raw {
        TODO()
    }

    fun build(db: CodeBuilder) {
        for (instruction in instructions) db.instruction()
    }
}

@MethodScopeDsl
class MethodScope<T : Any, R : Any>(
    val owner: KlassDesc<T>,
    val className: String,
    val returnType: KlassDesc<R>,
    hasThis: Boolean = true,
    val isConstructor: Boolean = false,
) {

    private val params = mutableListOf<ParamRef<*>>()
    private var paramCounter = 0
    private var receiverParam: ReceiverRef<T>? =
        if (hasThis) generateReceiverParam() else null

    private var codeScope: CodeScope<R>? = null
    private val flagsScope = FlagsScope.MethodFlagsScope(className)

    private var canDefineParams = true


    private fun <R : Any> generateParam(name: String, type: KlassDesc<R>): ParamRef<R> {
        if (!canDefineParams) throw ParamDefinitionInCodeError(name)
        return ParamRef(name, type, paramCounter++).also { params.add(it) }
    }

    private fun generateReceiverParam(): ReceiverRef<T> =
        ReceiverRef(owner).also { receiverParam = it }

    fun getReceiver(): ReceiverRef<T> {
        return receiverParam ?: throw NoParamFoundError("this")
    }

    fun receiver(): EagerDelegate<ReceiverRef<T>> = EagerDelegate { _, _ -> getReceiver() }

    fun <R : Any> parameter(name: String, type: KlassDesc<R>): ParamRef<R> = generateParam(name, type)
    inline fun <reified R : Any> parameter(name: String): ParamRef<R> = parameter(name, withType<R>())
    inline fun <reified R : Any> parameter(): EagerDelegate<ParamRef<R>> = EagerDelegate { _, property ->
        parameter(property.name)
    }

    fun code(body: CodeScope<R>.() -> Unit) {
        canDefineParams = false
        val newParams = buildList {
            val tmp = receiverParam
            if (tmp != null) add(tmp)
            addAll(params)
        }

        if (codeScope == null) codeScope = CodeScope(returnType, className, newParams)
        codeScope?.body()
    }

    fun access(body: FlagsScope.MethodFlagsScope.() -> Unit) {
        flagsScope.body()
    }

    fun build(): MethodRef {
        val flags = flagsScope.build()
        val methodTypeDesc = MethodTypeDesc.of(
            returnType.classDesc,
            params.sortedBy { it.order }.map { it.type.classDesc }
        )

        return MethodRef(
            owner = owner.classDesc,
            name = className,
            methodTypeDesc = methodTypeDesc,
            flags = flags,
            isConstructor = isConstructor,
            code = { codeScope?.build(it) }
        )
    }
}

private inline fun <reified T : Any, reified R : Any> method(
    name: String,
    builder: MethodScope<T, R>.() -> Unit,
): MethodRef {
    return MethodScope(withType<T>(), name, withType<R>())
        .apply { builder() }.build()
}

fun main() {
    val ref = method<NumberCenas, Int>("Cu") {
        val receiver by receiver()
        val a by parameter<Int>()
        val b by parameter<String>()
        val c by parameter<String>()

        access {
            private()
        }

        code {
            load(receiver)
            load(a)
            load(b)
            load(c)
            ret()
        }
    }
}

