package pt.rafap.klassfile.builders

import pt.rafap.klassfile.utils.FlagsScopeDsl
import pt.rafap.klassfile.utils.NoAccessSpecifierError
import java.lang.classfile.ClassFile.*

@Suppress("unused")
@FlagsScopeDsl
sealed class FlagsScope(private val scopeName: String) {

    private companion object {
        const val ACCESS_MASK = ACC_PUBLIC or ACC_PRIVATE or ACC_PROTECTED
    }

    protected var flags = 0
    private var access = 0

    fun applyFlag(flag: Int) {
        when (flag) {
            ACC_PUBLIC,
            ACC_PRIVATE,
            ACC_PROTECTED,
                -> {
                if (access != 0) {
                    error(
                        "$scopeName already has an access modifier " +
                                "(${modifierName(access)}). Cannot also apply ${modifierName(flag)}."
                    )
                }
                access = flag
            }

            else -> {
                if ((flags and flag) != 0) {
                    error("$scopeName already contains flag ${modifierName(flag)}.")
                }
                flags = flags or flag
            }
        }
    }

    fun build(): Int {
        if (access == 0) {
            throw NoAccessSpecifierError(scopeName)
        }

        return access or flags
    }

    private fun modifierName(flag: Int) = when (flag) {
        ACC_PUBLIC -> "public"
        ACC_PRIVATE -> "private"
        ACC_PROTECTED -> "protected"
        ACC_STATIC -> "static"
        ACC_FINAL -> "final"
        ACC_ABSTRACT -> "abstract"
        ACC_SYNCHRONIZED -> "synchronized"
        ACC_BRIDGE -> "bridge"
        ACC_VARARGS -> "varargs"
        ACC_NATIVE -> "native"
        ACC_STRICT -> "strict"
        ACC_SYNTHETIC -> "synthetic"
        ACC_VOLATILE -> "volatile"
        ACC_TRANSIENT -> "transient"
        ACC_ENUM -> "enum"
        ACC_SUPER -> "super"
        else -> "0x${flag.toString(16)}"
    }

    class ClassFlagsScope(scopeName: String) : FlagsScope("class $scopeName") {
        fun public() = applyFlag(ACC_PUBLIC)
        fun protected() = applyFlag(ACC_PROTECTED)
        fun private() = applyFlag(ACC_PRIVATE)

        fun final() = applyFlag(ACC_FINAL)
        fun abstract() = applyFlag(ACC_ABSTRACT)
        fun super_() = applyFlag(ACC_SUPER)
        fun synthetic() = applyFlag(ACC_SYNTHETIC)
    }

    class MethodFlagsScope(scopeName: String) : FlagsScope("method $scopeName") {
        fun public() = applyFlag(ACC_PUBLIC)
        fun protected() = applyFlag(ACC_PROTECTED)
        fun private() = applyFlag(ACC_PRIVATE)

        fun static() = applyFlag(ACC_STATIC)
        fun final() = applyFlag(ACC_FINAL)
        fun synchronized() = applyFlag(ACC_SYNCHRONIZED)
        fun bridge() = applyFlag(ACC_BRIDGE)
        fun varargs() = applyFlag(ACC_VARARGS)
        fun native() = applyFlag(ACC_NATIVE)
        fun abstract() = applyFlag(ACC_ABSTRACT)
        fun strict() = applyFlag(ACC_STRICT)
        fun synthetic() = applyFlag(ACC_SYNTHETIC)
    }

    class FieldFlagsScope(scopeName: String) : FlagsScope("field $scopeName") {
        fun public() = applyFlag(ACC_PUBLIC)
        fun protected() = applyFlag(ACC_PROTECTED)
        fun private() = applyFlag(ACC_PRIVATE)

        fun static() = applyFlag(ACC_STATIC)
        fun final() = applyFlag(ACC_FINAL)
        fun volatile() = applyFlag(ACC_VOLATILE)
        fun transient() = applyFlag(ACC_TRANSIENT)
        fun synthetic() = applyFlag(ACC_SYNTHETIC)
        fun enum() = applyFlag(ACC_ENUM)
    }
}