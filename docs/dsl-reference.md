# DSL reference

This is the public DSL inventory. Start with [Builders and scopes](builders-and-scopes.md) to understand which APIs are available in each nested block. For exact stack effects, see [References and operand stack](references-and-stack.md); for loop and branch syntax, see [Control flow and arrays](control-flow-and-arrays.md).

## Class builder

| API                                                   | Purpose                 | Rules                                                                     |
|-------------------------------------------------------|-------------------------|---------------------------------------------------------------------------|
| `klassFile<T>(name) { ... }`                          | Entry point             | `T` must be an interface or abstract class; a concrete class is rejected. |
| `access { ... }`                                      | Class flags             | Select exactly one visibility modifier; required before build.            |
| `field<T> { ... }` / `defineField(name, type)`        | Field declaration       | Returns `FieldRef`; fields are private by default.                        |
| `method<R> { ... }` / `defineMethod<R>(name)`         | Method declaration      | Returns `MethodRef`; method access must be configured.                    |
| `constructor { ... }`                                 | Constructor declaration | If omitted, a public no-argument constructor is generated.                |
| `getter` / `setter` / `defineGetter` / `defineSetter` | Accessor declaration    | Conventional generated names are `getX` and `setX`.                       |

`T` determines the generated hierarchy: an interface is implemented; an abstract class is extended. KlassFile validates abstract method implementations. The current API supports one inherited interface or abstract superclass, not an arbitrary hierarchy.

## Flags

Every flag scope requires exactly one of `public()`, `protected()`, or `private()`. Repeating a flag or selecting a second visibility raises a DSL error.

| Scope  | Additional flags                                                                                                      |
|--------|-----------------------------------------------------------------------------------------------------------------------|
| Class  | `final()`, `abstract()`, `super_()`, `synthetic()`                                                                    |
| Method | `static()`, `final()`, `synchronized()`, `bridge()`, `varargs()`, `native()`, `abstract()`, `strict()`, `synthetic()` |
| Field  | `static()`, `final()`, `volatile()`, `transient()`, `synthetic()`, `enum()`                                           |

Fields default to `private()`. Methods created with `method` or `defineMethod` have no default access: use `access { public() }`. The default constructor body uses `public()`.

## Method builder

| API                                      | Purpose                              | Constraint                                           |
|------------------------------------------|--------------------------------------|------------------------------------------------------|
| `param<T>()` / `defineParam(name, type)` | Parameter declaration                | Must occur before `code`.                            |
| `receiver()`                             | Gets the implicit receiver reference | Instance members only.                               |
| `access { ... }`                         | Method flags                         | Required for ordinary methods.                       |
| `code { ... }`                           | Emits bytecode                       | Must emit `ret()` and leave no tracked stack values. |

For non-`Unit` methods, push the declared return type immediately before `ret()`. For `Unit`, call `ret()` with an empty stack.

## Code builder inventory

| Family            | APIs                                                                                                                                                       |
|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Values and locals | `ldc`, `nullValue`, `load`, `store`, `loadReceiver`, `local`, `defineLocal`, `inc`, `dec`, `ret`                                                           |
| Arithmetic        | `add`, `sub`, `mul`, `div`, `rem`, `neg`, `shl`, `shr`, `uShr`, `and`, `or`, `xor`, `convert`, `cmp`                                                       |
| Stack             | `pop`, `pop2`, `dup`, `dupX1`, `dupX2`, `dup2`, `dup2X1`, `dup2X2`, `swap`                                                                                 |
| Fields            | `getStatic`, `putStatic`, `getField`, `putField`, `putThisField`, `FieldRef.load`, `FieldRef.store`                                                        |
| Objects/calls     | `new`, `defaultCtor`, `instantiate`, `instantiateField`, `findMethod`, `invokeMethod`, `invokeVirtual`, `invokeInterface`, `invokeStatic`, `invokeSpecial` |
| Types/arrays      | `checkCast`, `instanceOf`, `newArray`, `arrayLength`, `arrayLoad`, `arrayStore`, array `get`/`set` operators                                               |
| Branches          | `label`, `defineLabel`, `LabelRef.bind`, `goto`, all `if*` functions, comparisons, `if_`, `while_`, `for_`                                                 |

`newMultiArray` is declared but currently throws `NotImplementedError`.

## Utilities

| API                                                                      | Purpose                                                                        |
|--------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `klassDescOf<T>()`                                                       | Reified `KlassDesc<T>`.                                                        |
| `classDesc(name/Class/KClass/KType)`                                     | Java `ClassDesc`; string inputs are binary names such as `"java.lang.String"`. |
| `nullableClassDesc(KClass)`                                              | Boxed descriptor for nullable primitive use cases.                             |
| `Method.toMethodRef`, `Constructor.toMethodRef`, `KFunction.toMethodRef` | Convert reflection objects to `MethodRef`.                                     |
| `resolveMethod(...)`                                                     | Standalone public method/constructor resolver.                                 |
| `println { ... }` / `println(ref)`                                       | `CodeScope` extensions that emit `System.out.println`.                         |

## Output

`klassFile` returns `KlassFileBuilder.Klass`:

| API                     | Result                                                                                                                        |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `bytes`                 | Final class-file byte array.                                                                                                  |
| `writeToDisk()`         | Writes the class under its binary name.                                                                                       |
| `writeAndGetClass()`    | Writes and loads the class.                                                                                                   |
| `writeAndGetInstance()` | Writes, loads, and instantiates through Kotlin reflection; needs a concrete class with an accessible no-argument constructor. |
| `load<T>()`             | Loads the generated name from KlassFile's class loader; use after writing it.                                                 |

## Current limits

- Generates classes only; no parsing or transformation of existing class files.
- One interface or one abstract superclass as inherited type.
- No exception handling, `invokedynamic`, lambdas, switches, or multi-dimensional array generation.
- No public arbitrary-`CodeBuilder` escape hatch.
- Stack validation covers supported instructions, not every JVM verifier rule.
