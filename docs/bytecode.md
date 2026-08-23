# Bytecode instructions

All bytecode is emitted inside a method's `code` block. KlassFile tracks the simulated operand stack while these helpers run.

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
