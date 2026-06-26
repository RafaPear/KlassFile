@file:Suppress("unused")

package pt.rafap.klassfile.utils

import pt.rafap.klassfile.models.InvokeType
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.models.ParamRef
import java.lang.classfile.ClassFile.ACC_PUBLIC
import java.lang.constant.ConstantDescs.INIT_NAME
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod


/**
 * Converts a Java [Method] into a [MethodRef] using explicit owner and return types.
 *
 * @param owner the class that owns the method.
 * @param type the expected return type.
 * @return the converted [MethodRef].
 */
fun <O : Any, R : Any> Method.toMethodRef(
    owner: KlassDesc<O>,
    type: KlassDesc<R>,
): MethodRef<O, R> {
    val params = parameters.mapIndexed { i, param ->
        val name = param.name ?: "arg$i"
        ParamRef(name, KlassDesc(param.type), i)
    }

    val invokeType = InvokeType.fromFlags(name, modifiers)

    return MethodRef(
        name,
        owner = owner,
        type,
        params,
        modifiers,
        invokeType,
    )
}

/** Converts a Java [Method] into a [MethodRef] using reified owner and return types. */
inline fun <reified O : Any, reified R : Any> Method.toMethodRef() =
    toMethodRef(klassDescOf<O>(), klassDescOf<R>())

/**
 * Converts a Java [Constructor] into a constructor-style [MethodRef].
 *
 * Kotlin synthetic constructor markers are skipped so only real constructor
 * parameters remain in the resulting reference.
 *
 * @param owner the class that owns the constructor.
 * @return the converted [MethodRef], or `null` if the constructor only represents a synthetic marker.
 */
fun <O : Any, R : Any> Constructor<*>.toMethodRef(
    owner: KlassDesc<O>,
): MethodRef<O, R>? {
    val params = parameters.mapIndexed { i, param ->
        if (param.name != null && param.name == "DefaultConstructorMarker") return null

        val name = param.name ?: "arg$i"
        ParamRef(name, KlassDesc(param.type), i)
    }

    val type = KlassDesc<R>(Unit::class.java)

    return MethodRef(
        INIT_NAME,
        owner = owner,
        type,
        params,
        modifiers,
        InvokeType.SPECIAL,
    )
}

/** Converts a Java [Constructor] into a [MethodRef] using reified owner and return types. */
inline fun <reified O : Any, reified R : Any> Constructor<*>.toMethodRef() =
    toMethodRef<O, R>(klassDescOf<O>())

/**
 * Converts a Kotlin [KFunction] into a [MethodRef] using explicit owner and return types.
 *
 * @param owner the class that owns the function.
 * @param type the expected return type.
 * @return the converted [MethodRef].
 */
fun <O : Any, R : Any> KFunction<*>.toMethodRef(
    owner: KlassDesc<O>,
    type: KlassDesc<R>,
): MethodRef<O, R> {
    val params = parameters.mapIndexed { i, param ->
        val name = param.name ?: "arg$i"
        ParamRef(name, KlassDesc(param.type), i)
    }

    val name = if (this.javaConstructor != null) INIT_NAME else name

    val modifiers = this.javaConstructor?.modifiers ?: this.javaMethod?.modifiers
    ?: throw IllegalArgumentException("KFunction must have a corresponding Java method or constructor to determine modifiers.")

    val invokeType = InvokeType.fromFlags(name, modifiers)

    return MethodRef(
        name,
        owner,
        type,
        params,
        modifiers,
        invokeType
    )
}

/** Converts a Kotlin [KFunction] into a [MethodRef] using reified owner and return types. */
inline fun <reified O : Any, reified R : Any> KFunction<*>.toMethodRef() =
    toMethodRef(klassDescOf<O>(), klassDescOf<R>())

/**
 * Resolves a public method from the given owner and return type and converts it into a [MethodRef].
 *
 * This utility performs overload resolution in multiple stages:
 *
 * 1. Resolve every public method or constructor with the requested [name].
 * 2. If only one candidate exists, return it immediately.
 * 3. If multiple candidates remain, narrow them by the provided parameter list.
 * 4. If a single candidate remains after filtering, return it.
 * 5. Otherwise report the ambiguity with the remaining candidates.
 *
 * Only public members visible through Java reflection are considered.
 *
 * Example:
 * ```kotlin
 * resolveMethod(
 *     name = "println",
 *     owner = klassDescOf<PrintStream>(),
 *     type = klassDescOf<Unit>(),
 *     params = ParamRef("arg0", klassDescOf<String>(), 0)
 * )
 * ```
 *
 * @param name the method name to resolve.
 * @param owner the class that owns the target member.
 * @param type the expected return type.
 * @param params optional parameters used to disambiguate overloads.
 * @return a resolved [MethodRef] representing the target method.
 * @throws NoSuchMethodException if no public member with the given name exists.
 * @throws IllegalStateException if multiple overloads remain after filtering.
 */
fun <O : Any, R : Any> resolveMethod(
    name: String,
    owner: KlassDesc<O>,
    type: KlassDesc<R>,
    vararg params: ParamRef<*>,
): MethodRef<O, R> {
    val kClass = owner.kClass

    val thisRefInfo = MethodRef(
        name,
        owner,
        type,
        params.toList(),
        flags = ACC_PUBLIC,
        InvokeType.STATIC
    )

    // First, try to find the method by the name

    val crudeKotlinMethods = kClass.declaredFunctions.map { it.toMethodRef(owner, type) }

    val crudeJavaMethods = kClass.java.methods.map { it.toMethodRef(owner, type) }

    val crudeKotlinConstructors = kClass.constructors.map { it.toMethodRef(owner, type) }

    val crudeJavaConstructors = kClass.java.declaredConstructors.mapNotNull { it.toMethodRef<O, R>(owner) }

    val crudeMethods = (crudeJavaMethods + crudeKotlinMethods).toSet()
    val crudeConstructors = (crudeJavaConstructors + crudeKotlinConstructors).toSet()

    var methods: List<MethodRef<O, R>> = (crudeMethods + crudeConstructors).filter {
        it.flags and ACC_PUBLIC != 0 && it.name == name
    }

    if (methods.isEmpty()) {
        throw NoSuchMethodException(
            buildString {
                appendLine("COULD NOT RESOLVE PUBLIC METHOD FOR:")
                appendLine("  $thisRefInfo")

                appendLine("\nAVAILABLE CONSTRUCTORS:")
                crudeConstructors.forEach {
                    appendLine("  $it")
                }

                appendLine("\nAVAILABLE METHODS:")
                crudeMethods.forEach {
                    appendLine("  $it")
                }
            }
        )
    }

    // If only one method is present then the method is resolved
    // as it has no overloads.
    if (methods.size == 1) {
        return methods.first()
    }

    // Finaly, resolve by the parameters if not resolved already

    val paramTypes = params.map { it.type.classDesc }

    val filtered =
        methods.filter { method ->
            paramTypes == method.params.map { it.type.classDesc }
        }

    if (filtered.size == 1)
        return filtered.first()

    if (filtered.isNotEmpty())
        methods = filtered


    // If the method is still ambiguous, throw an error with all the candidates
    // This will happen when the caller of this function does not specify any
    // overload resolution information and the class has multiple methods with
    // the same name.
    error(
        buildString {
            appendLine("AMBIGUOUS METHOD RESOLUTION FOR")
            appendLine("  $thisRefInfo")
            appendLine()
            appendLine("CANDIDATE METHODS:")

            methods.forEach {
                appendLine("  $it")
            }
        }
    )
}