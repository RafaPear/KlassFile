package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.classDesc
import java.lang.constant.ClassDesc
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
/**
 * Couples a JVM [ClassDesc] with the matching Kotlin [KClass].
 *
 * Equality and hashing are based on the JVM descriptor so that equivalent
 * references compare consistently even when they originate from different forms.
 */
data class KlassDesc<T : Any>(
    val classDesc: ClassDesc,
    val kClass: KClass<T>,
) {
    /** Creates a descriptor from a Kotlin [KType], preserving nullability where possible. */
    constructor(type: KType) : this(
        classDesc(type),
        type.classifier as? KClass<T> ?: Any::class as KClass<T>
    )

    /** Creates a descriptor from a Java [Class]. */
    constructor(type: Class<*>) : this(classDesc(type), type.kotlin as KClass<T>)

    /** Creates a descriptor directly from a Kotlin [KClass]. */
    constructor(type: KClass<T>) : this(classDesc(type), type)

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
