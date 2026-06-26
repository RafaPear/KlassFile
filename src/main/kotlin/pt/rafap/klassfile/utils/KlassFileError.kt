package pt.rafap.klassfile.utils

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.builders.FlagsScope
import pt.rafap.klassfile.models.InvokeType
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.models.StackValue

/** Base type for all DSL validation and bytecode generation failures. */
sealed class KlassFileError : Exception()

/** Thrown when a scope is built without selecting an access modifier. */
class NoAccessSpecifierError(scopeName: String) : KlassFileError() {
    override val message: String = "No access specifier provided for '$scopeName'. " +
            "Please provide one with the 'access' scope"
}

/** Thrown when an abstract class is instantiated directly. */
class AbstractInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is abstract and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

/** Thrown when an interface is instantiated directly. */
class InterfaceInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is an interface and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

/** Thrown when an enum is instantiated directly. */
class EnumInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is an enum and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

/** Thrown when a generated type does not expose a usable constructor. */
class NoConstructorError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' does not have a constructor. " +
            "Please provide a constructor to instantiate."
}

/** Thrown when a generated class tries to inherit from a final type. */
class BadInheritError(inheritor: String, inherited: String) : KlassFileError() {
    override val message: String = "Class '$inheritor' cannot inherit from '$inherited' as it is final. " +
            "Please provide an interface or non-final class to inherit from."
}

/** Thrown when a parameter is referenced but not present in the current scope. */
class NoParamFoundError(paramName: String) : KlassFileError() {
    override val message: String = "The current context does not contain a '$paramName' parameter. " +
            "Please provide a parameter with the name '$paramName' in the current context."
}

/** Thrown when a parameter is declared after code emission has already started. */
class ParamDefinitionInCodeError(paramName: String) : KlassFileError() {
    override val message: String = "The parameter '$paramName' is being defined inside a code block." +
            "Please define the parameter outside and before the code block."
}

/** Thrown when a local slot index does not map to a known parameter. */
class InvalidSlotIndexError(index: Int) : KlassFileError() {
    override val message: String = "The requested slot index '$index' does not exist." +
            "Create the parameter for that slot before trying to access it."
}

/** Thrown when a nested raw bytecode block is attempted. */
class NestedRawBlockError : KlassFileError() {
    override val message: String = "A raw code block is being defined inside another raw code block." +
            "Please define the inner raw code block outside of the outer one."
}

/** Thrown when a method reference is used with an incompatible invocation opcode. */
class InvokeReferenceError(invokeType: InvokeType, ref: MethodRef<*, *>) : KlassFileError() {
    override val message: String = "The method reference '${ref}' is not valid for the invoke type '$invokeType'. " +
            "Please provide a valid method reference for the given invoke type."
}

/** Thrown when code emission finishes with leftover stack values. */
class StackUnderflowError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String = "The stack in '${codeScope.scopeName}' is empty and cannot be popped. " +
            "Please ensure that the stack has enough elements before popping."
}

/** Thrown when the simulated operand stack top has an unexpected type. */
class StackTypeMismatchError(expected: StackValue, actual: StackValue, codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String =
        "The stack in '${codeScope.scopeName}' has a type mismatch. " +
                "Expected '${expected.type.classDesc.displayName()}', but found '${actual.type.classDesc.displayName()}'. " +
                "Please ensure that the stack has the correct types before popping."
}

/** Thrown when code completion is attempted with a non-empty stack. */
class StackNotEmptyError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String = "The stack is not empty after executing '${codeScope.scopeName}'. " +
            "Please ensure that the stack is empty before finishing the scope."
}

/** Thrown when a code block completes without emitting a return instruction. */
class NoReturnError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String = "The code block '${codeScope.scopeName}' does not have a return statement. " +
            "Please ensure that the code block has a return statement before finishing the scope."
}

class DuplicateAccessError(flagsScope: FlagsScope, access: Int, flag: Int) : KlassFileError() {
    override val message: String = "${flagsScope.scopeName} already has an access modifier " +
            "(${flagsScope.modifierName(access)}). Cannot also apply ${flagsScope.modifierName(flag)}."
}

class IncompatibleAccessError(flagsScope: FlagsScope, flag: Int) : KlassFileError() {
    override val message: String = "${flagsScope.scopeName} already has an access modifier " +
            "(${flagsScope.modifierName(flag)}). Cannot apply another access modifier."
}

class NoCodeBlockDefinedError(methodRef: MethodRef<*, *>) : KlassFileError() {
    override val message: String = "The method '${methodRef}' does not have a code block defined. " +
            "Please provide a code block for the method before finishing the scope."
}