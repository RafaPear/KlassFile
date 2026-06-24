package pt.rafap.klassfile.builders

import pt.rafap.klassfile.utils.FlagsScopeDsl
import pt.rafap.klassfile.utils.NoAccessSpecifierError
import java.lang.classfile.ClassFile.*

@Suppress("unused")
@FlagsScopeDsl
sealed class FlagsScope(private val className: String) {
    protected var flags: Int = ACC_PRIVATE
    private var hasBeenSet: Boolean = false

    fun applyFlag(flag: Int) {
        flags = flags or flag
        hasBeenSet = true
    }

    fun build(): Int {
        if (!hasBeenSet) {
            throw NoAccessSpecifierError(className)
        }
        return flags
    }

    class ClassFlagsScope(className: String) : FlagsScope(className) {

        fun public() = applyFlag(ACC_PUBLIC)
        fun protected() = applyFlag(ACC_PROTECTED)
        fun private() = applyFlag(ACC_PRIVATE)

        fun final() = applyFlag(ACC_FINAL)
        fun abstract() = applyFlag(ACC_ABSTRACT)
        fun super_() = applyFlag(ACC_SUPER)

        fun synthetic() = applyFlag(ACC_SYNTHETIC)
    }

    class MethodFlagsScope(className: String) : FlagsScope(className) {

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

    class FieldFlagsScope(className: String) : FlagsScope(className) {
        fun protected() = applyFlag(ACC_PROTECTED)
        fun private() = applyFlag(ACC_PRIVATE)

        fun static() = applyFlag(ACC_STATIC)

        fun final() = applyFlag(ACC_FINAL)

        fun volatile() = applyFlag(ACC_VOLATILE)

        fun transient() = applyFlag(ACC_TRANSIENT)

        fun synthetic() = applyFlag(ACC_SYNTHETIC)

        fun enum() = applyFlag(ACC_ENUM)
    }

    class ModuleFlagsScope(className: String) : FlagsScope(className) {

        fun open() = applyFlag(ACC_OPEN)

        fun synthetic() = applyFlag(ACC_SYNTHETIC)

        fun mandated() = applyFlag(ACC_MANDATED)
    }

    class ModuleRequiresFlagsScope(className: String) : FlagsScope(className) {

        fun transitive() = applyFlag(ACC_TRANSITIVE)

        fun staticPhase() = applyFlag(ACC_STATIC_PHASE)

        fun synthetic() = applyFlag(ACC_SYNTHETIC)

        fun mandated() = applyFlag(ACC_MANDATED)
    }

    class MethodParameterFlagsScope(className: String) : FlagsScope(className) {

        fun final() = applyFlag(ACC_FINAL)

        fun synthetic() = applyFlag(ACC_SYNTHETIC)

        fun mandated() = applyFlag(ACC_MANDATED)
    }
}

