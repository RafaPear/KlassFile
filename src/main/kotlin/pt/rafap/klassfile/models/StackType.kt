package pt.rafap.klassfile.models

enum class StackType {
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    VOID,
    REFERENCE;

    fun isPrimitive() = this !in listOf(REFERENCE, VOID)

    fun isReference() = !isPrimitive()

    fun isEqualTo(other: StackType): Boolean {
        return when (this) {
            INT -> other == INT
            LONG -> other == LONG
            FLOAT -> other == FLOAT
            DOUBLE -> other == DOUBLE
            VOID -> other == VOID
            REFERENCE -> false
        }
    }

    companion object {
        val KlassDesc<*>.stackType: StackType
            get() = toStackType()

        val StackValue.stackType: StackType
            get() = type.toStackType()

        fun KlassDesc<*>.toStackType(): StackType {
            return when (kClass) {
                Boolean::class,
                Byte::class,
                Short::class,
                Char::class,
                Int::class,
                    -> INT

                Long::class -> LONG
                Float::class -> FLOAT
                Double::class -> DOUBLE
                Unit::class -> VOID

                else -> REFERENCE
            }
        }
    }
}