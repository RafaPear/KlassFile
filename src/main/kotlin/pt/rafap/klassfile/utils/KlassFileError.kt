package pt.rafap.klassfile.utils

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.models.InvokeType
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.models.StackValue

sealed class KlassFileError : Exception()
class NoAccessSpecifierError(scopeName: String) : KlassFileError() {
    override val message: String = "No access specifier provided for '$scopeName'. " +
            "Please provide one with the 'access' scope"
}

class AbstractInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is abstract and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

class InterfaceInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is an interface and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

class EnumInstanceError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' is an enum and cannot be instantiated. " +
            "Please provide a concrete class with a constructor to instantiate."
}

class NoConstructorError(className: String) : KlassFileError() {
    override val message: String = "Class '$className' does not have a constructor. " +
            "Please provide a constructor to instantiate."
}

class BadInheritError(inheritor: String, inherited: String) : KlassFileError() {
    override val message: String = "Class '$inheritor' cannot inherit from '$inherited' as it is final. " +
            "Please provide an interface or non-final class to inherit from."
}

class NoParamFoundError(paramName: String) : KlassFileError() {
    override val message: String = "The current context does not contain a '$paramName' parameter. " +
            "Please provide a parameter with the name '$paramName' in the current context."
}

class ParamDefinitionInCodeError(paramName: String) : KlassFileError() {
    override val message: String = "The parameter '$paramName' is being defined inside a code block." +
            "Please define the parameter outside and before the code block."
}

class InvalidSlotIndexError(index: Int) : KlassFileError() {
    override val message: String = "The requested slot index '$index' does not exist." +
            "Create the parameter for that slot before trying to access it."
}

class NestedRawBlockError : KlassFileError() {
    override val message: String = "A raw code block is being defined inside another raw code block." +
            "Please define the inner raw code block outside of the outer one."
}

class InvokeReferenceError(invokeType: InvokeType, ref: MethodRef<*, *>) : KlassFileError() {
    override val message: String = "The method reference '${ref}' is not valid for the invoke type '$invokeType'. " +
            "Please provide a valid method reference for the given invoke type."
}

class StackUnderflowError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String = "The stack in '${codeScope.scopeName}' is empty and cannot be popped. " +
            "Please ensure that the stack has enough elements before popping."
}

class StackTypeMismatchError(expected: StackValue, actual: StackValue, codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String =
        "The stack in '${codeScope.scopeName}' has a type mismatch. " +
                "Expected '${expected.type.classDesc.displayName()}', but found '${actual.type.classDesc.displayName()}'. " +
                "Please ensure that the stack has the correct types before popping."
}

class StackNotEmptyError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String = "The stack is not empty after executing '${codeScope.scopeName}'. " +
            "Please ensure that the stack is empty before finishing the scope."
}

class NoReturnError(codeScope: CodeScope<*, *>) : KlassFileError() {
    init {
        codeScope.printStack()
    }

    override val message: String = "The code block '${codeScope.scopeName}' does not have a return statement. " +
            "Please ensure that the code block has a return statement before finishing the scope."
}