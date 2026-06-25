package pt.rafap.klassfile.utils

import pt.rafap.klassfile.models.InvokeType
import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.models.ParamRef
import java.lang.classfile.ClassFile.ACC_PUBLIC
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.CD_void
import java.lang.constant.ConstantDescs.INIT_NAME
import java.lang.constant.MethodTypeDesc
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

fun <O : Any, R : Any> resolveMultipleMethods(
    name: String,
    owner: KlassDesc<O>,
    type: KlassDesc<R>,
): List<MethodRef<O, R>> =
    owner.kClass.java.methods
        .filter { it.name == name }
        .mapNotNull { it.toMethodRef(owner, type) }

/**
 * Creates a [MethodTypeDesc] from a return type and a list of argument types.
 *
 * This is a thin wrapper around [MethodTypeDesc.of].
 *
 * @param returnType the method return type. Defaults to [CD_void].
 * @param argTypes the method argument types, in declaration order.
 * @return the corresponding [MethodTypeDesc].
 */
fun methodDesc(
    returnType: ClassDesc? = null,
    vararg argTypes: ClassDesc,
): MethodTypeDesc {
    val newReturnType = returnType ?: CD_void
    return MethodTypeDesc.of(newReturnType, *argTypes)
}

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

inline fun <reified O : Any, reified R : Any> Method.toMethodRef() =
    toMethodRef(klassDescOf<O>(), klassDescOf<R>())

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

inline fun <reified O : Any, reified R : Any> Constructor<*>.toMethodRef() =
    toMethodRef<O, R>(klassDescOf<O>())

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

inline fun <reified O : Any, reified R : Any> KFunction<*>.toMethodRef() =
    toMethodRef(klassDescOf<O>(), klassDescOf<R>())

/**
 * Resolves a public method from the given class and converts it into a [MethodRef].
 *
 * This utility performs overload resolution in multiple stages:
 *
 * 1. Resolves all public methods with the given [name].
 * 2. If only one method exists, it is immediately returned.
 * 3. If [returnType] is provided, methods are filtered by return type.
 * 4. If [params] are provided, methods are filtered by parameter types.
 * 5. If a single method is resolved at any stage, it is returned.
 * 6. If multiple candidates remain, an ambiguity error is thrown.
 *
 * Only public methods visible through Java reflection (`kclass.java.methods`)
 * are considered.
 *
 * Example:
 * ```kotlin
 * resolveMethod(
 *     name = "println",
 *     kclass = PrintStream::class,
 *     returnType = CD_void,
 *     args = ClassDesc.of("java.lang.String")
 * )
 * ```
 *
 * @param name
 * Name of the method to resolve.
 *
 * @param kclass
 * Target class where the method will be searched.
 *
 * @param returnType
 * Optional expected return type used to disambiguate overloaded methods.
 *
 * @param params
 * Optional parameter types used for overload resolution.
 *
 * @return
 * A resolved [MethodRef] representing the target method.
 *
 * @throws NoSuchMethodException
 * If no public method with the given name exists in the class.
 *
 * @throws IllegalStateException
 * If multiple overloads still match after applying all resolution filters.
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

    val crudeKotlinMethods = kClass.declaredFunctions.mapNotNull { it.toMethodRef(owner, type) }

    val crudeJavaMethods = kClass.java.methods.mapNotNull { it.toMethodRef(owner, type) }

    val crudeKotlinConstructors = kClass.constructors.mapNotNull { it.toMethodRef(owner, type) }

    val crudeJavaConstructors = kClass.java.declaredConstructors.mapNotNull { it.toMethodRef<O, R>(owner) }

    val crudeMethods = (crudeJavaMethods + crudeKotlinMethods).toSet()
    val crudeConstructors = (crudeJavaConstructors + crudeKotlinConstructors).toSet()

    var methods = (crudeMethods + crudeConstructors).filter {
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