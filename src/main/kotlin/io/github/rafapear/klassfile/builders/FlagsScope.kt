package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.utils.*
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
        const val ACCESS_MASK =
            ACC_PUBLIC or ACC_PRIVATE or ACC_PROTECTED
    }

    private var flags = 0

    private val incompatibleFlags: Set<Set<Int>>
        get() = when (this) {
            is ClassFlagsScope -> classIncompatible
            is MethodFlagsScope -> methodIncompatible
            is FieldFlagsScope -> fieldIncompatible
        }

    private val fieldIncompatible = setOf(
        setOf(ACC_FINAL, ACC_VOLATILE),
    )

    private val methodIncompatible = setOf(
        setOf(ACC_ABSTRACT, ACC_FINAL),
        setOf(ACC_ABSTRACT, ACC_NATIVE),
        setOf(ACC_ABSTRACT, ACC_STATIC),
        setOf(ACC_ABSTRACT, ACC_STRICT),
        setOf(ACC_ABSTRACT, ACC_SYNCHRONIZED),
    )

    private val classIncompatible = setOf(
        setOf(ACC_FINAL, ACC_ABSTRACT),
    )

    /**
     * Applies a JVM flag while enforcing access-modifier exclusivity
     * and incompatible-flag constraints.
     */
    fun applyFlag(flag: Int) {
        if ((flag and ACCESS_MASK) != 0) {
            applyAccessFlag(flag)
            return
        }

        applyRegularFlag(flag)
    }

    private fun checkDuplicate(flag: Int): Boolean {
        return (flags and flag) != 0
    }

    /**
     * Applies an access modifier.
     *
     * Only one of `public`, `protected` or `private` may be present.
     */
    private fun applyAccessFlag(flag: Int) {
        if (checkDuplicate(flag)) return

        val existingAccess = flags and ACCESS_MASK

        if (existingAccess != 0) {
            throw IncompatibleAccessError(
                this,
                existingAccess,
                flag
            )
        }

        flags = flags or flag
    }

    /**
     * Applies a non-access JVM flag while checking incompatible combinations
     * and duplicate flags.
     */
    private fun applyRegularFlag(flag: Int) {
        if (checkDuplicate(flag)) return

        val incompatibleGroup = incompatibleFlags.firstOrNull { group ->
            flag in group &&
                    group.any { existing ->
                        existing != flag && (flags and existing) != 0
                    }
        }

        if (incompatibleGroup != null) {
            val existingFlag = incompatibleGroup.first {
                it != flag && (flags and it) != 0
            }

            throw IncompatibleFlagsError(
                this,
                existingFlag,
                flag,
            )
        }

        flags = flags or flag
    }

    /**
     * Returns the final bit mask once a single access modifier has been selected.
     */
    fun build(): Int {
        if ((flags and ACCESS_MASK) == 0) {
            throw NoAccessSpecifierError(scopeName)
        }

        return flags
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
                else -> throw UnknownFlagError(this, flag)
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
                else -> throw UnknownFlagError(this, flag)
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
                else -> throw UnknownFlagError(this, flag)
            }
        }
    }
}