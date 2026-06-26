package pt.rafap.klassfile.builders

import pt.rafap.klassfile.utils.DuplicateAccessError
import pt.rafap.klassfile.utils.FlagsScopeDsl
import pt.rafap.klassfile.utils.IncompatibleAccessError
import pt.rafap.klassfile.utils.NoAccessSpecifierError
import java.lang.classfile.ClassFile.*

@Suppress("unused")
/**
 * Base DSL for assembling JVM access flags with validation.
 *
 * Exactly one access modifier must be selected before [build] can succeed, and
 * the concrete subclasses expose only the modifier combinations that are legal
 * for their respective declaration kind.
 */
@FlagsScopeDsl
sealed class FlagsScope(val scopeName: String) {

    private companion object {
        const val ACCESS_MASK = ACC_PUBLIC or ACC_PRIVATE or ACC_PROTECTED
    }

    protected var flags = 0
    private var access = 0

    /** Applies a JVM flag while enforcing access-modifier exclusivity. */
    fun applyFlag(flag: Int) {
        when (flag) {
            ACC_PUBLIC,
            ACC_PRIVATE,
            ACC_PROTECTED,
                -> {
                if (access != 0) {
                    throw IncompatibleAccessError(this, access)
                }
                access = flag
            }

            else -> {
                if ((flags and flag) != 0) {
                    throw DuplicateAccessError(this, flags, flag)
                }
                flags = flags or flag
            }
        }
    }

    /** Returns the final bit mask once a single access modifier has been selected. */
    fun build(): Int {
        if (access == 0) {
            throw NoAccessSpecifierError(scopeName)
        }

        return access or flags
    }

    /**
     * Converts a JVM flag constant to a readable modifier label.
     *
     * @param flag the JVM flag value.
     * @return a readable modifier name.
     */
    abstract fun modifierName(flag: Int): String

    /** Flag scope for classes and interfaces. */
    class ClassFlagsScope(scopeName: String) : FlagsScope("class $scopeName") {
        /** Applies `public` visibility. */
        fun public() = applyFlag(ACC_PUBLIC)
        /** Applies `protected` visibility. */
        fun protected() = applyFlag(ACC_PROTECTED)
        /** Applies `private` visibility. */
        fun private() = applyFlag(ACC_PRIVATE)

        /** Applies `final`. */
        fun final() = applyFlag(ACC_FINAL)
        /** Applies `abstract`. */
        fun abstract() = applyFlag(ACC_ABSTRACT)
        /** Applies `super`. */
        fun super_() = applyFlag(ACC_SUPER)
        /** Applies `synthetic`. */
        fun synthetic() = applyFlag(ACC_SYNTHETIC)

        override fun modifierName(flag: Int): String {
            return when (flag) {
                ACC_PUBLIC -> "public"
                ACC_PRIVATE -> "private"
                ACC_PROTECTED -> "protected"
                ACC_FINAL -> "final"
                ACC_ABSTRACT -> "abstract"
                ACC_SUPER -> "super"
                ACC_SYNTHETIC -> "synthetic"
                else -> throw IllegalArgumentException("Unknown flag: ${flag.toHexString()}")
            }
        }
    }

    /** Flag scope for methods and constructors. */
    class MethodFlagsScope(scopeName: String) : FlagsScope("method $scopeName") {
        /** Applies `public` visibility. */
        fun public() = applyFlag(ACC_PUBLIC)
        /** Applies `protected` visibility. */
        fun protected() = applyFlag(ACC_PROTECTED)
        /** Applies `private` visibility. */
        fun private() = applyFlag(ACC_PRIVATE)

        /** Applies `static`. */
        fun static() = applyFlag(ACC_STATIC)
        /** Applies `final`. */
        fun final() = applyFlag(ACC_FINAL)
        /** Applies `synchronized`. */
        fun synchronized() = applyFlag(ACC_SYNCHRONIZED)
        /** Applies `bridge`. */
        fun bridge() = applyFlag(ACC_BRIDGE)
        /** Applies `varargs`. */
        fun varargs() = applyFlag(ACC_VARARGS)
        /** Applies `native`. */
        fun native() = applyFlag(ACC_NATIVE)
        /** Applies `abstract`. */
        fun abstract() = applyFlag(ACC_ABSTRACT)
        /** Applies `strict`. */
        fun strict() = applyFlag(ACC_STRICT)
        /** Applies `synthetic`. */
        fun synthetic() = applyFlag(ACC_SYNTHETIC)

        override fun modifierName(flag: Int): String {
            return when (flag) {
                ACC_PUBLIC -> "public"
                ACC_PRIVATE -> "private"
                ACC_PROTECTED -> "protected"
                ACC_STATIC -> "static"
                ACC_FINAL -> "final"
                ACC_SYNCHRONIZED -> "synchronized"
                ACC_BRIDGE -> "bridge"
                ACC_VARARGS -> "varargs"
                ACC_NATIVE -> "native"
                ACC_ABSTRACT -> "abstract"
                ACC_STRICT -> "strict"
                ACC_SYNTHETIC -> "synthetic"
                else -> throw IllegalArgumentException("Unknown flag: ${flag.toHexString()}")
            }
        }
    }

    /** Flag scope for fields. */
    class FieldFlagsScope(scopeName: String) : FlagsScope("field $scopeName") {
        /** Applies `public` visibility. */
        fun public() = applyFlag(ACC_PUBLIC)
        /** Applies `protected` visibility. */
        fun protected() = applyFlag(ACC_PROTECTED)
        /** Applies `private` visibility. */
        fun private() = applyFlag(ACC_PRIVATE)

        /** Applies `static`. */
        fun static() = applyFlag(ACC_STATIC)
        /** Applies `final`. */
        fun final() = applyFlag(ACC_FINAL)
        /** Applies `volatile`. */
        fun volatile() = applyFlag(ACC_VOLATILE)
        /** Applies `transient`. */
        fun transient() = applyFlag(ACC_TRANSIENT)
        /** Applies `synthetic`. */
        fun synthetic() = applyFlag(ACC_SYNTHETIC)
        /** Applies `enum`. */
        fun enum() = applyFlag(ACC_ENUM)

        override fun modifierName(flag: Int): String {
            return when (flag) {
                ACC_PUBLIC -> "public"
                ACC_PRIVATE -> "private"
                ACC_PROTECTED -> "protected"
                ACC_STATIC -> "static"
                ACC_FINAL -> "final"
                ACC_VOLATILE -> "volatile"
                ACC_TRANSIENT -> "transient"
                ACC_SYNTHETIC -> "synthetic"
                ACC_ENUM -> "enum"
                else -> throw IllegalArgumentException("Unknown flag: ${flag.toHexString()}")
            }
        }
    }
}