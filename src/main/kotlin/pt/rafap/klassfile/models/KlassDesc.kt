package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.classDesc
import pt.rafap.klassfile.utils.nullableClassDesc
import java.lang.constant.ClassDesc
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

@Suppress("UNCHECKED_CAST")
/**
 * Couples a JVM [ClassDesc] with the matching Kotlin [KClass].
 *
 * Equality and hashing are based on the JVM descriptor so that equivalent
 * references compare consistently even when they originate from different forms.
 */
open class KlassDesc<T : Any>(
    val classDesc: ClassDesc,
    val kClass: KClass<T>,
) {
    /** Creates a descriptor from a Kotlin [KType], preserving nullability where possible. */
    constructor(type: KType) : this(
        classDesc(type),
        type.classifier as? KClass<T> ?: Any::class as KClass<T>
    )

    constructor(type: KType, boxed: Boolean) : this(
        classDesc =
            if (boxed) nullableClassDesc(type.jvmErasure)
            else classDesc(type),
        type.classifier as? KClass<T> ?: Any::class as KClass<T>
    )

    /** Creates a descriptor from a Java [Class]. */
    constructor(type: Class<*>) : this(classDesc(type), type.kotlin as KClass<T>)

    /** Creates a descriptor directly from a Kotlin [KClass]. */
    constructor(type: KClass<T>) : this(classDesc(type), type)

    data class ArrayKlassDesc<T:Any> (val elementType: KlassDesc<T>) : KlassDesc<Array<T>>(
        classDesc = elementType.classDesc.arrayType(),
        kClass = Array::class as KClass<Array<T>>
    )

    fun array() = ArrayKlassDesc(this)

    /**
     * Returns whether this descriptor accepts [other] by JVM assignability rules.
     *
     * @param other the candidate descriptor.
     * @return `true` when [other] can be assigned to this descriptor.
     */
    fun isAssignableFrom(other: KlassDesc<*>): Boolean {
        if (classDesc == other.classDesc)
            return true

        val expected = kClass.java
        val actual = other.kClass.java

        if (expected.isPrimitive || actual.isPrimitive)
            return false

        return expected.isAssignableFrom(actual)
    }

    /**
     * Compares descriptors by JVM [classDesc].
     *
     * @param other the value to compare against.
     * @return `true` when both descriptors represent the same JVM type.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KlassDesc<*>) return false

        if (classDesc != other.classDesc) return false

        return true
    }

    /**
     * Returns a hash code based on [classDesc].
     */
    override fun hashCode(): Int {
        return classDesc.hashCode()
    }

    /**
     * Returns a human-readable display name for this descriptor.
     */
    override fun toString(): String {
        return classDesc.displayName()
    }
}
