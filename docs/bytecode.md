# Bytecode instructions

All bytecode is emitted inside a method's `code` block. KlassFile tracks the simulated operand stack while these helpers run.

## Two API levels

The low-level instruction APIs map closely to JVM operations. They are not an interpreter and do not hide the emitted bytecode: each helper records the corresponding Java `CodeBuilder` operation and updates KlassFile's stack model. Examples include `ldc`, `load`, `store`, `getField`, `putField`, `invokeVirtual`, `goto`, `ifICmpLt`, `arrayLoad`, `iinc`-style `inc`, and stack operations such as `dup`.

The higher-level DSL is built from those same primitives:

| Higher-level helper | Composes low-level operations such as |
|---|---|
| `if_(condition) { body }` | labels, `goto`, label binding, and a conditional branch |
| `while_(condition) do_ { body }` | labels, conditional branch, `goto` |
| `for_(range) do_ { body }` | local initialization, comparisons, labels, `inc`, `goto` |
| `getter(field)` / `setter(field)` | receiver load, field access, parameter load, `ret` |
| `instantiate<T>()` | `new`, `dup`, constructor resolution, `invokespecial` |
| `putThisField(field) { ... }` | `loadReceiver`, value generation, `putfield` |

Choose the high-level form for the common case. Use low-level operations when you need an exact bytecode shape, a custom branch layout, or explicit stack manipulation.

## Values, locals, and return

```kotlin
code {
    val total by local<Int>()
    total set 0

    load(total)
    ldc(42)
    add()
    store(total)

    load(total)
    ret()
}
```

- `ldc(value)` loads `String`, `Int`, `Long`, `Float`, and `Double` constants. Other supported uses load a class literal.
- `nullValue()` emits `aconst_null`.
- `load(ref)` and `store(ref)` read and write parameters or locals.
- `loadReceiver()` loads the implicit `this` parameter of an instance method.
- `ret()` emits the correct JVM return instruction for the method's declared result type.

Local variables must be initialized before being read. `inc(ref)` and `dec(ref, amount)` operate on `Int` local slots or parameters.

## Numeric operations

The numeric helpers support `Int`, `Long`, `Float`, and `Double` as appropriate:

```kotlin
add(); sub(); mul(); div(); rem(); neg()
shl(); shr(); uShr()
and(); or(); xor()
convert<Long>()
cmp()
```

Most operations have an overload that first loads two references, for example `add(left, right)`. Binary operations require compatible operand types; shift counts are `Int`.

### Numeric instruction matrix

| API | Required stack before (bottom → top) | Result | Supported types |
|---|---|---|---|
| `add`, `sub`, `mul`, `div`, `rem` | `[left, right]` | `[result]` | matching `Int`, `Long`, `Float`, or `Double` |
| `neg` | `[value]` | `[result]` | `Int`, `Long`, `Float`, or `Double` |
| `shl`, `shr`, `uShr` | `[value, shift]` | `[result]` | `Int` or `Long` value; `Int` shift |
| `and`, `or`, `xor` | `[left, right]` | `[result]` | matching `Int` or `Long` |
| `convert<T>()` | `[value]` | `[converted value]` | numeric conversions implemented by the JVM instruction set |
| `cmp` | `[left, right]` | `[Int]` | matching `Long`, `Float`, or `Double` |

`add(a, b)`, `sub(a, b)`, `mul(a, b)`, `div(a, b)`, `rem(a, b)`, `shl(a, b)`, `shr(a, b)`, `uShr(a, b)`, `and(a, b)`, `or(a, b)`, `xor(a, b)`, and `cmp(a, b)` load both `TypedRef` arguments before running the stack form. A `TypedRef` can be a parameter, local, or field. The shift helpers also provide `shl(a, shift: Int)`, `shr(a, shift: Int)`, and `uShr(a, shift: Int)`.

`neg(a)` loads one `TypedRef`. `convert<T>()` and the no-argument arithmetic forms consume values that you have already put on the stack.

## Fields

Use references returned by `field`, `defineField`, `getter`, or `setter`:

```kotlin
putThisField(count) { ldc(1) }

count.load()
count.store { ldc(2) }
```

Lower-level alternatives are `getField`, `putField`, `getStatic`, and `putStatic`. Instance field instructions require an object reference on the stack; KlassFile's convenience helpers load the current receiver when appropriate.

## Low-level branches and labels

KlassFile exposes the complete conditional-branch families used by the JVM `if*` instructions:

| JVM family | KlassFile functions | Stack operands |
|---|---|---|
| zero comparison | `ifEq`, `ifNe`, `ifLt`, `ifLe`, `ifGt`, `ifGe` | one `int`-category value |
| integer comparison | `ifICmpEq`, `ifICmpNe`, `ifICmpLt`, `ifICmpLe`, `ifICmpGt`, `ifICmpGe` | two `int`-category values |
| reference comparison | `ifACmpEq`, `ifACmpNe` | two references |
| null comparison | `ifNull`, `ifNonNull` | one reference |

Every branch receives a `LabelRef`. Create one with `val target by label()`, emit `goto(target)` or an `if*` instruction, then emit `target.bind()` at its destination. The [control-flow chapter](control-flow-and-arrays.md#low-level-branches) shows the exact stack requirements and the comparison sugar built on top of these functions.

Other low-level instruction families currently exposed include local loads/stores, numeric and bitwise arithmetic, casts, `instanceof`, object creation, array creation/access, field access, every invocation kind (`virtual`, `interface`, `static`, and `special`), and the JVM stack-rearrangement instructions. Each API is documented with its required stack shape in [References and operand stack](references-and-stack.md).

## Calls and objects

```kotlin
instantiate<StringBuilder>()
invokeMethod(existingMethod)
```

`instantiate<T>` emits object creation, duplicates the reference, resolves a constructor, and calls it. `invokeMethod(ref)` chooses an invocation opcode from the `MethodRef`; `invokeVirtual`, `invokeInterface`, `invokeStatic`, and `invokeSpecial` are available when an explicit opcode is required.

`checkCast<T>()` and `instanceOf<T>()` emit the corresponding reference instructions. `newArray<T>()`, `newArray(descriptor)`, and `newMultiArray(descriptor, dimensions)` create arrays.

### Object and reference operations

| API | Required stack before | Stack after | Notes |
|---|---|---|---|
| `new(type)` | `[]` | `[uninitialized object]` | Explicit `KlassDesc` form. |
| `instantiate<T> { ... }` | `[constructor arguments]` | `[object]` | The lambda provides constructor parameter types; it does not load argument values. |
| `checkCast<T>()` | `[reference]` | `[reference as T]` | Runtime JVM cast. |
| `instanceOf<T>()` | `[reference]` | `[Int]` | JVM boolean result is tracked as `Int`. |
| `newArray<T>()` | `[Int size]` | `[T array]` | Creates a one-dimensional array. |
| `newMultiArray(...)` | — | — | Declared but currently throws `NotImplementedError`. |

`new(type)` is intentionally lower level: it leaves an uninitialized reference on the stack. In normal code, prefer `instantiate` so that the required `dup` and constructor invocation are emitted for you.

## Stack rearrangement

The following direct JVM stack operations are exposed: `pop`, `pop2`, `dup`, `dupX1`, `dupX2`, `dup2`, `dup2X1`, `dup2X2`, and `swap`. They enforce the JVM category rules: `Long` and `Double` are category 2; all other supported values are category 1. Use them only when the higher-level helpers cannot express the desired bytecode, because incorrect stack order quickly makes code hard to read.

## Unsupported areas

KlassFile currently exposes helpers for only part of the JVM instruction set. Exception handling and `invokedynamic` are not currently provided. There is no public arbitrary-`CodeBuilder` escape hatch; unsupported instructions require extending the library itself.
