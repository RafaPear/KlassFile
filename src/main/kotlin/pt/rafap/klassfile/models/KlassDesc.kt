package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.classDesc
import java.lang.constant.ClassDesc
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
data class KlassDesc<T : Any>(
    val classDesc: ClassDesc,
    val kClass: KClass<T>,
) {
    constructor(type: KType) : this(
        classDesc(type),
        type.classifier as? KClass<T> ?: Any::class as KClass<T>
    )

    constructor(type: Class<*>) : this(classDesc(type), type.kotlin as KClass<T>)

    constructor(type: KClass<T>) : this(classDesc(type), type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KlassDesc<*>) return false

        if (classDesc != other.classDesc) return false

        return true
    }

    override fun hashCode(): Int {
        return classDesc.hashCode()
    }

    override fun toString(): String {
        return classDesc.displayName()
    }
}
