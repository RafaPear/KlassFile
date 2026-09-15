# References and operand stack

KlassFile emits stack-machine bytecode. Before using an instruction, know both the **reference value** it accepts and the values it expects on the operand stack.

In the stack diagrams below, the rightmost item is the top of the stack. For example, `[receiver, value]` means `value` is on top and is consumed first.

## Declaring and using values

```kotlin
val parameter by param<Int>()  // declaration: outside code { ... }

code {
    val local by local<Int>()  // declaration: inside code { ... }
    local set 0                // initializes the local

    load(parameter)           // pushes the parameter
    store(local)               // pops into the local
}
```

`param<T>()` and `local<T>()` are delegates: the Kotlin property name becomes the reference name used in diagnostics. A local is not initialized when declared; loading it before assigning or storing to it raises `UninitializedLocalVariableError`.

`LocalRef<T>.set` has these forms:

```kotlin
total set 0          // pushes literal 0, then stores it
total set parameter  // loads an OrderedRef, then stores it
total set Unit       // stores the value already on the stack
```

The second form accepts `OrderedRef<T>` only, so it cannot directly copy a field. To copy a field, use `field.load()` followed by `store(total)`.

## Stack effect reference

| API | Required stack before | Stack after | Notes |
|---|---|---|---|
| `ldc(value)` | `[]` | `[value]` | Supports `String`, `Int`, `Long`, `Float`, and `Double`; other uses load a class literal. |
| `nullValue()` | `[]` | `[null]` | Emits `aconst_null`. |
| `load(ref)` | `[]` | `[ref value]` | `ref` must be a `ParamRef` or `LocalRef`. |
| `store(ref)` | `[value]` | `[]` | `ref` must be a `ParamRef` or `LocalRef`; type must match. |
| `loadReceiver()` | `[]` | `[this]` | Only available in an instance method. |
| `ret()` | `[result]` or `[]` | unreachable | A non-`Unit` method needs its declared result on top; `Unit` needs no value. |
| `pop()` | `[category-1 value]` | `[]` | A category-1 value is anything except `Long`/`Double`. |
| `pop2()` | `[Long]`, `[Double]`, or `[a, b]` | `[]` | The two-value form needs two category-1 values. |
| `dup()` | `[category-1 value]` | `[value, value]` | The `dupX*` and `dup2X*` variants are also available. |

At the end of a reachable `code` scope, the tracked stack must be empty and at least one `ret()` must have been emitted.

## Fields

For a field declared as `val count by field<Int>()`, these are the safe convenience forms:

```kotlin
count.load()               // pushes the field value; loads `this` if non-static
count.store { ldc(10) }    // pops the value produced by the block into the field
putThisField(count) { ldc(10) }
```

The lower-level forms require these stack shapes:

| API | Required stack before | Stack after |
|---|---|---|
| `getStatic(ref)` | `[]` | `[value]` |
| `putStatic(ref)` | `[value]` | `[]` |
| `getField(ref)` | `[receiver]` | `[value]` |
| `putField(ref)` | `[receiver, value]` | `[]` |

`getField(name, owner, type)` and `putField(name, owner, type)` accept metadata directly; the `FieldRef` overloads are preferable because they retain the correct owner and type.

## Calls and constructors

For a `MethodRef` with parameters `(A, B)`:

| Invocation kind | Required stack before | Stack after |
|---|---|---|
| Instance (`invokeVirtual`, `invokeInterface`, `invokeSpecial`) | `[receiver, A, B]` | `[result]`, or `[]` for `Unit` |
| Static (`invokeStatic`) | `[A, B]` | `[result]`, or `[]` for `Unit` |

`invokeMethod(ref)` selects an opcode from the reference, but it does **not** push the receiver or arguments. Push them first:

```kotlin
loadReceiver()
load(value)
invokeMethod(setValue)
```

`instantiate<T> { ... }` emits `new`, `dup`, resolves the selected constructor, and invokes it. If the constructor has arguments, push their values **before** calling `instantiate`; the builder block only describes their types for reflection-based overload resolution:

```kotlin
ldc("text")
instantiate<StringBuilder> {
    arg<String>()
}
```

## Numeric helpers

`add`, `sub`, `mul`, `div`, and `rem` consume two equal numeric types and push one result. `neg` consumes and pushes one numeric value. `shl`, `shr`, and `uShr` consume a value plus an `Int` shift count. `and`, `or`, and `xor` accept `Int` or `Long` operands.

Each binary numeric helper has a convenience form such as `add(left, right)`. Its arguments are `TypedRef<*, *>`, which means a parameter, local, or field reference is accepted. The helper loads both values in argument order, then emits the operation.

