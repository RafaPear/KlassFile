package pt.rafap.klassfile.utils

sealed class KlassFileError: Exception()
class NoAccessSpecifierError(className: String) : KlassFileError() {
    override val message: String = "No access specifier provided for class '$className'. " +
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
    override val message: String = "The current context does not contain a '$paramName' parameter. "+
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