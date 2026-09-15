package io.github.rafapear.klassfile.utils

import io.github.rafapear.klassfile.builders.CodeScope
import io.github.rafapear.klassfile.builders.FlagsScope
import io.github.rafapear.klassfile.builders.MethodScope
import io.github.rafapear.klassfile.models.*

/** Base type for all DSL validation and bytecode generation failures. */
private val LIBRARY_PACKAGES = listOf(
    "io.github.rafapear.klassfile",
    "java.lang.classfile",
    "jdk.internal.classfile"
)

const val richErrorsEnabled = true

@NoTestCoverage
abstract class KlassFileError : RuntimeException() {
    override fun fillInStackTrace(): Throwable {
        super.fillInStackTrace()

        if (!richErrorsEnabled) return this

        val newStack = mutableListOf<StackTraceElement>()
        for (elm in stackTrace) {

            if (LIBRARY_PACKAGES.any { elm.className.lowercase().contains(it) })
                continue

            newStack.addLast(elm)
        }

        stackTrace = newStack.toTypedArray()
        return this
    }
}

/** Thrown when a scope is built without selecting an access modifier. */
@NoTestCoverage
class NoAccessSpecifierError(scopeName: String) : KlassFileError() {
    override val message: String = "No access specifier provided for '$scopeName'. " +
            "Please provide one with the 'access' scope"
}

/** Thrown when an abstract class is instantiated directly. */
@NoTestCoverage
class AbstractInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is abstract and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

/** Thrown when an interface is instantiated directly. */
@NoTestCoverage
class InterfaceInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is an interface and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

/** Thrown when an enum is instantiated directly. */
@NoTestCoverage
class EnumInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is an enum and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

/** Thrown when a generated type does not expose a usable constructor. */
@NoTestCoverage
class NoConstructorError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' does not have a constructor. " +
            "Please provide a constructor to instantiate."
}

/** Thrown when a generated class tries to inherit from a final type. */
@NoTestCoverage
class BadInheritError(inheritor: String, inherited: String) : KlassFileError() {
    override val message: String = "Class '$inheritor' cannot inherit from '$inherited' as it is final. " +
            "Please provide an interface or non-final class to inherit from."
}

/** Thrown when a parameter is referenced but not present in the current scope. */
@NoTestCoverage
class NoParamFoundError(paramName: String) : KlassFileError() {
    override val message: String = "The current context does not contain a '$paramName' parameter. " +
            "Please provide a parameter with the name '$paramName' in the current context."
}

/** Thrown when a parameter is declared after code emission has already started. */
@NoTestCoverage
class ParamDefinitionInCodeError(paramName: String) : KlassFileError() {
    override val message: String = "The parameter '$paramName' is being defined inside a code block. " +
            "Please define the parameter outside and before the code block."
}

/** Thrown when a local slot index does not map to a known parameter. */
@NoTestCoverage
class InvalidSlotIndexError(index: Int) : KlassFileError() {
    override val message: String = "The requested slot index '$index' does not exist. " +
            "Create the parameter for that slot before trying to access it."
}

/** Thrown when a nested raw bytecode block is attempted. */
@NoTestCoverage
class NestedRawBlockError : KlassFileError() {
    override val message: String = "A raw code block is being defined inside another raw code block. " +
            "Please define the inner raw code block outside of the outer one."
}

/** Thrown when a method reference is used with an incompatible invocation opcode. */
@NoTestCoverage
class InvokeReferenceError(invokeType: InvokeType, ref: MethodRef<*, *>) : KlassFileError() {
    override val message: String = "The method reference '${ref}' is not valid for the invoke type '$invokeType'. " +
            "Please provide a valid method reference for the given invoke type."
}

/** Thrown when code emission finishes with leftover stack values. */
@NoTestCoverage
class StackUnderflowError(codeScope: CodeScope<*, *>, expected: KlassDesc<*>?) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String = buildString {
        append("The stack in '${codeScope.scopeName}' is empty and cannot be popped")
        if (expected != null) append(", but a value of type '${expected.classDesc.displayName()}' was expected. ")
        else append(". ")

        append("Please ensure that the stack has enough elements before popping.")
    }
}

/** Thrown when the simulated operand stack top has an unexpected type. */
@NoTestCoverage
class StackTypeMismatchError(expected: KlassDesc<*>, actual: KlassDesc<*>, codeScope: CodeScope<*, *>) :
    KlassFileError() {
    init {
        codeScope.printInfo()
    }

    constructor(expected: StackValue, actual: StackValue, codeScope: CodeScope<*, *>) :
            this(expected.type, actual.type, codeScope)

    override val message: String =
        "The stack in '${codeScope.scopeName}' has a type mismatch. " +
                "Expected '${expected.classDesc.displayName()}', but found '${actual.classDesc.displayName()}'. " +
                "Please ensure that the stack has the correct types before popping."
}

@NoTestCoverage
class StackTopIsNotAnArrayError(actual: StackValue, codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String =
        "The stack in '${codeScope.scopeName}' has a type mismatch. " +
                "Expected an array type, but found '${actual.type.classDesc.displayName()}'. " +
                "Please ensure that the stack has an array type before popping."
}

@NoTestCoverage
class StackReferenceTypeExpectedError(actual: StackValue, codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String =
        "The stack in '${codeScope.scopeName}' has a type mismatch. " +
                "Expected a reference type, but found '${actual.type.classDesc.displayName()}'. " +
                "Please ensure that the stack has a reference type before popping."
}

/** Thrown when code completion is attempted with a non-empty stack. */
@NoTestCoverage
class StackNotEmptyError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String = "The stack is not empty after executing '${codeScope.scopeName}'. " +
            "Please ensure that the stack is empty before finishing the scope."
}

@NoTestCoverage
class StackVoidTypeError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String = "Cannot pop stack for void type in '${codeScope.scopeName}'. " +
            "Please ensure that the stack has a valid type before popping."
}

/** Thrown when a code block completes without emitting a return instruction. */
@NoTestCoverage
class NoReturnError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String = "The code block '${codeScope.scopeName}' does not have a return statement. " +
            "Please ensure that the code block has a return statement before finishing the scope."
}

@NoTestCoverage
class UninitializedLocalVariableError(localRef: LocalRef<*>, codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String =
        "The local variable '${localRef.name}' in '${codeScope.scopeName}' is being accessed before it has been initialized. " +
                "Please ensure that the local variable is initialized before accessing it."
}

@NoTestCoverage
class StackSizeMismatch(codeScope: CodeScope<*, *>, actual: Int, expected: Int) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String =
        "The stack at the goto target in '${codeScope.scopeName}' does not match the expected state. " +
                "Expected $expected value(s) on the stack, but found $actual. " +
                "All jumps to the same label must leave the operand stack in the same state."
}

@NoTestCoverage
class UnknownFlagError(flagsScope: FlagsScope, flag: Int) : KlassFileError() {
    override val message: String = "${flagsScope.scopeName} has an unknown flag value '$flag'. " +
            "Please provide a valid JVM flag for the scope."
}

@NoTestCoverage
class IncompatibleAccessError(flagsScope: FlagsScope, access: Int, flag: Int) : KlassFileError() {
    override val message: String = "${flagsScope.scopeName} has an access modifier " +
            "(${flagsScope.modifierName(access)}) that is incompatible with another flag. " +
            "Cannot apply ${flagsScope.modifierName(flag)} together with ${flagsScope.modifierName(access)}."
}

@NoTestCoverage
class IncompatibleFlagsError(flagsScope: FlagsScope, flag1: Int, flag2: Int) : KlassFileError() {
    override val message: String = "${flagsScope.scopeName} has a flag that is incompatible with another flag. " +
            "Cannot apply ${flagsScope.modifierName(flag1)} and ${flagsScope.modifierName(flag2)} together."
}

@NoTestCoverage
class NoCodeBlockDefinedError(methodRef: MethodRef<*, *>) : KlassFileError() {
    override val message: String = "The method '${methodRef}' does not have a code block defined. " +
            "Please provide a code block for the method before finishing the scope."
}

@NoTestCoverage
class UnsupportedKotlinArrayOfPrimitivesError : KlassFileError() {
    override val message: String =
        "\n\nKotlin's Array<T> is not supported by KlassFileApi when T is a primitive type (e.g. Array<Int>, Array<Float>).\n" +
                "On the JVM, Array<Int> is represented as Integer[], whereas primitive arrays are represented as int[], float[], etc.\n" +
                "Because these are different JVM types, this library cannot infer the intended representation automatically.\n" +
                "Use the corresponding primitive array type instead (e.g. IntArray, LongArray, FloatArray, DoubleArray, CharArray, BooleanArray, ByteArray or ShortArray).\n\n"
}

@NoTestCoverage
class ArrayTypeInNewArrayError(type: KlassDesc<*>, codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String =
        "Cannot create a new array of type '${type.classDesc.displayName()}' in '${codeScope.scopeName}'. " +
                "Use multidimensional arrays or the corresponding primitive array types instead. " +
                "Please ensure that the type is a valid array type before creating a new array."
}

@NoTestCoverage
class NestedCodeScopes(methodScope: MethodScope<*, *>) : KlassFileError() {
    override val message: String =
        "A code block is being defined inside another code block in '${methodScope.name}'. " +
                "Please define the inner code block outside of the outer one."
}

@NoTestCoverage
class CannotCheckCastPrimitiveTypeError(codeScope: CodeScope<*, *>, type: KlassDesc<*>, actual: KlassDesc<*>) : KlassFileError() {
    init {
        codeScope.printInfo()
    }

    override val message: String =
        "Cannot check cast from '${actual.classDesc.displayName()}' to '${type.classDesc.displayName()}' in '${codeScope.scopeName}'. " +
                "Please ensure that the types are compatible before performing the check cast."
}

@NoTestCoverage
class MissingImplementationsError(
    missing: List<MethodRef<*, *>>,
    implementedMethods: List<MethodRef<*, *>>,
) : KlassFileError() {
    override val message: String = buildString {
        append("The following methods are missing implementations:\n")
        for (method in missing) {
            append("- ${method}\n")
        }
        append("\nImplemented methods:\n")
        if (implementedMethods.isEmpty()) {
            append("- None\n")
        }
        for (method in implementedMethods) {
            append("- ${method}\n")
        }
    }
}