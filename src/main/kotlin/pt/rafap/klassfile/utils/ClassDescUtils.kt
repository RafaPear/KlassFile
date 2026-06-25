package pt.rafap.klassfile.utils

import pt.rafap.klassfile.models.KlassDesc
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure


/**
 * Creates a [ClassDesc] from a binary class name using [ClassDesc.of].
 *
 * The name must use the binary-name form expected by the classfile API, such as `java.lang.String`.
 *
 * @param name the binary name of the class.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(name: String): ClassDesc = ClassDesc.of(name)

/**
 * Creates a [ClassDesc] from a [Class].
 *
 * Primitive Java types are mapped to predefined constants (for example, [CD_int] for `Int::class.java`), while
 * reference and array types are converted from their JVM descriptor strings.
 *
 * @param clazz the [Class] to create a [ClassDesc] for.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(clazz: Class<*>): ClassDesc = classDesc(clazz.kotlin)

/**
 * Creates a [ClassDesc] from a [KClass].
 *
 * This overload delegates to [classDesc] that accepts a [Class].
 *
 * @param klass the [KClass] to create a [ClassDesc] for.
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
    else -> {
        if (klass.isNullable()) nullableClassDesc(klass)
        else klass.java.describeConstable().orElseThrow()
    }
}

private fun KClass<*>.isNullable(): Boolean = starProjectedType.isMarkedNullable

/**
 * Creates a [ClassDesc] from a [KClass].
 *
 * This overload delegates to [classDesc] that accepts a [Class].
 *
 * @param klass the [KClass] to create a [ClassDesc] for.
 * @return the corresponding [ClassDesc].
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
 * Creates a [ClassDesc] from a [KClass].
 *
 * This overload delegates to [classDesc] that accepts a [Class].
 *
 * @param kParameter the [KClass] to create a [ClassDesc] for.
 * @return the corresponding [ClassDesc].
 */
fun classDesc(kParameter: KType): ClassDesc {
    val kClass = kParameter.jvmErasure

    return if (!kParameter.isMarkedNullable) classDesc(kClass)
    else nullableClassDesc(kClass)
}

/**
 * Creates a [ClassDesc] from a reified type parameter.
 *
 * This is the most ergonomic overload when the type is known at compile time.
 *
 * @param T the type to create a [ClassDesc] for.
 * @return the corresponding [ClassDesc].
 */
inline fun <reified T> classDesc(): ClassDesc = classDesc(T::class)

inline fun <reified T : Any> klassDescOf(): KlassDesc<T> =
    KlassDesc(classDesc<T>(), T::class)

@Suppress("UNCHECKED_CAST")
fun <T : Any> ClassDesc.toKlassDesc(): KlassDesc<T> {
    return when (this) {
        CD_byte -> KlassDesc(this, Byte::class as KClass<T>)
        CD_short -> KlassDesc(this, Short::class as KClass<T>)
        CD_int -> KlassDesc(this, Int::class as KClass<T>)
        CD_long -> KlassDesc(this, Long::class as KClass<T>)
        CD_float -> KlassDesc(this, Float::class as KClass<T>)
        CD_double -> KlassDesc(this, Double::class as KClass<T>)
        CD_char -> KlassDesc(this, Char::class as KClass<T>)
        CD_boolean -> KlassDesc(this, Boolean::class as KClass<T>)
        CD_void -> KlassDesc(this, Unit::class as KClass<T>)
        else -> {
            val kClass = Class.forName(this.toString()).kotlin as KClass<T>
            KlassDesc(this, kClass)
        }
    }
}