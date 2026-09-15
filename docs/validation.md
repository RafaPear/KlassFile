# Validation and errors

KlassFile does not replace JVM verification. It adds earlier checks for the bytecode patterns it models.

## What is checked

- Operand-stack underflow and expected stack types.
- Primitive/reference mismatches for many instructions.
- Return values and a non-empty stack at the end of a reachable code scope.
- Reads of uninitialized locals.
- Parameter declarations attempted after code emission starts.
- Compatible stack size at label and jump targets.
- Invalid field/method access flag combinations.
- Incorrect method invocation kinds.
- Missing implementations of abstract methods on the requested interface or abstract superclass.

Errors use `KlassFileError` subclasses with explanatory messages, such as `StackTypeMismatchError`, `StackUnderflowError`, `NoReturnError`, and `MissingImplementationsError`. The library removes its own internal frames from these exceptions' stack traces when rich errors are enabled.

## What is not guaranteed

KlassFile currently models only part of the JVM instruction set and does not claim full bytecode verification. In particular, generated code may still fail JVM loading or verification if it depends on a rule the DSL does not model or an instruction family the library does not yet support.

Always exercise generated classes in tests. The repository's examples under `src/test/kotlin/pt/rafap/klassfile/examples` are executable tests and cover interface implementations, loops, fields, arrays, Fibonacci, and bubble sort.

## Common mistakes

- Declare `param` values before entering `code`.
- Load a return value before calling `ret()` for a non-`Unit` method.
- Initialize every `local` before loading it.
- Load an instance receiver before using low-level instance field instructions.
- Supply parameter types to `findMethod` when resolving an overloaded method.
