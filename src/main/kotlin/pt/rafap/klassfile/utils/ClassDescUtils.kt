package pt.rafap.klassfile.utils

import pt.rafap.klassfile.models.KlassDesc
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

/**
 * Creates a [ClassDesc] from a binary class name using [ClassDesc.of].
 *
 * @param name the binary class name.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(name: String): ClassDesc = ClassDesc.of(name)

/**
 * Creates a [ClassDesc] from a Java [Class].
 *
 * @param clazz the Java class to convert.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(clazz: Class<*>): ClassDesc = classDesc(clazz.kotlin)

/**
 * Creates a [ClassDesc] from a Kotlin [KClass].
 *
 * Nullable primitive wrappers are used when the type is nullable.
 *
 * @param klass the Kotlin class to convert.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(klass: KClass<*>): ClassDesc = when (klass) {
    Byte::class -> CD_byte
    Short::class -> CD_short
    Int::class -> CD_int
    Long::class -> CD_long
    Float::class -> CD_float
    Double::class -> CD_double
    Char::class -> CD_char
    Boolean::class -> CD_boolean
    Unit::class -> CD_void
    String::class -> CD_String
    else -> if (klass.isNullable()) nullableClassDesc(klass) else klass.java.describeConstable().orElseThrow()
}

/**
 * Returns whether this class is represented as nullable in star-projected form.
 *
 * @return `true` when the projected type is nullable.
 */
private fun KClass<*>.isNullable(): Boolean = starProjectedType.isMarkedNullable

/**
 * Creates a nullable [ClassDesc] from a Kotlin [KClass].
 *
 * @param klass the Kotlin class to convert.
 * @return the nullable [ClassDesc] representation.
 */
fun nullableClassDesc(klass: KClass<*>): ClassDesc = when (klass) {
    Byte::class -> CD_Byte
    Short::class -> CD_Short
    Int::class -> CD_Integer
    Long::class -> CD_Long
    Float::class -> CD_Float
    Double::class -> CD_Double
    Char::class -> CD_Character
    Boolean::class -> CD_Boolean
    Unit::class -> CD_Void
    String::class -> CD_String
    else -> klass.java.describeConstable().orElseThrow()
}

/**
 * Creates a [ClassDesc] from a Kotlin [KType], preserving nullability.
 *
 * @param kParameter the type to convert.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(kParameter: KType): ClassDesc {
    val kClass = kParameter.jvmErasure

    return if (!kParameter.isMarkedNullable) classDesc(kClass) else nullableClassDesc(kClass)
}

/**
 * Creates a [ClassDesc] from a reified type parameter.
 *
 * @return the corresponding [ClassDesc].
 */
inline fun <reified T> classDesc(): ClassDesc = classDesc(T::class)

/**
 * Creates a [KlassDesc] from a reified type parameter.
 *
 * @return the corresponding [KlassDesc].
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> klassDescOf(): KlassDesc<T> {

    // Reject Array<Int>, Array<Float>, etc.
    if (T::class.starProjectedType == Array::class.starProjectedType) {
        val elementClass = typeOf<T>()
            .arguments
            .firstOrNull()
            ?.type
            ?.classifier as? KClass<*>
            ?: error("Unable to determine the array element type for ${T::class}")

        if (elementClass in setOf(
                Byte::class,
                Short::class,
                Int::class,
                Long::class,
                Float::class,
                Double::class,
                Char::class,
                Boolean::class
            )
        ) throw UnsupportedKotlinArrayOfPrimitivesError()


        return KlassDesc<T>(typeOf<T>().arguments[0].type!!).array() as KlassDesc<T>
    }

    // Primitive arrays (IntArray, FloatArray, ...)
    if (T::class.java.isArray) {
        val componentType = T::class.java.componentType
        return KlassDesc<T>(componentType).array() as KlassDesc<T>
    }

    return KlassDesc(T::class)
}
