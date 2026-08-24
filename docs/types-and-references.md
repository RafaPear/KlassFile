# Types and references

KlassFile separates **type descriptions** from **references**. A type description says what a JVM value is; a reference says where a value lives, which member can be called, or where a branch goes.

## Type descriptions

`KlassDesc<T>` combines a Java `ClassDesc` (class-file emission) with a Kotlin `KClass<T>` (reflection and validation). Normal DSL code uses reified types:

```kotlin
field<Int>()
defineMethod<String>("message") { /* ... */ }
param<IntArray>()
local<Long>()

val stringType = klassDescOf<String>()
val stringArrayType = stringType.array()
```

Use an explicit `KlassDesc` when it must be passed as a value:

```kotlin
defineField("value", klassDescOf<Int>())
new(klassDescOf<StringBuilder>())
checkCast(klassDescOf<CharSequence>())
```

The reified DSL APIs require `T : Any`; nullable types need an explicit `KType`-based descriptor. Generic arguments are erased by the JVM, so `List<String>` and `List<Int>` share one runtime descriptor. Primitive arrays such as `IntArray` and object arrays such as `Array<String>` are supported; `Array<Int>` is rejected because it means `Integer[]`, not `int[]`.

## Reference model

There is no single “reference” accepted everywhere. This table is the practical guide:

| Reference | Describes | Created by | Use it with | Do not use it with |
|---|---|---|---|---|
| `ParamRef<T>` | A method parameter in a local slot | `val x by param<T>()`, `defineParam` | `load`, `store`, `inc`, comparisons, ranges, array indexing, arithmetic helpers | Declaration after `code {}` |
| `LocalRef<T>` | A local variable in a local slot | `val x by local<T>()`, `defineLocal` | `load`, `store`, `inc`, comparisons, ranges, array indexing, arithmetic helpers | `load` before initialization |
| `ParamRef.ReceiverRef<T>` | The implicit `this` parameter | `receiver()` / `loadReceiver()` | Field and instance-call setup | Static-only logic |
| `FieldRef<O, T>` | A generated instance/static field | `field<T>()`, `defineField` | `field.load()`, `field.store {}`, `getField`, `putField`, arithmetic helpers | `load(field)`, ranges, or array-indexing sugar |
| `MethodRef<O, R>` | A method or constructor signature | `method`, `defineMethod`, `constructor`, `findMethod` | `invokeMethod`, `invokeVirtual`, `invokeInterface`, `invokeStatic`, `invokeSpecial` | `load` or arithmetic helpers |
| `LabelRef` | A branch destination | `val target by label()`, `defineLabel` | `goto`, `if*`, `if_`, loop builders | Any value-producing operation |

## `TypedRef` versus `OrderedRef`

These are API abstractions, not additional runtime entities:

```text
ParamRef ─┐
          ├─ OrderedRef ─ TypedRef
LocalRef ─┘

FieldRef ──────────────── TypedRef
MethodRef ─────────────── TypedRef
LabelRef ──────────────── neither
```

| Abstraction | Meaning | Implemented by | Practical consequence |
|---|---|---|---|
| `TypedRef<O, T>` | Has a known `KlassDesc<T>` type | Fields, methods, parameters, locals | Arithmetic helper overloads can accept a parameter, local, or field. A typed reference is not necessarily loadable. |
| `OrderedRef<T>` | Has a JVM local-variable `order` | Parameters and locals only | `load`, `store`, comparisons, ranges, and array sugar work with it. |

`OrderedRef` technically inherits `TypedRef<Any, T>`, but `ParamRef` and `LocalRef` do not have a meaningful member owner; accessing their `owner` property is invalid. The rule to remember is: **local-slot values use `OrderedRef`; fields use `FieldRef`; calls use `MethodRef`; branches use `LabelRef`.**

## Examples

```kotlin
val count by field<Int>()

defineMethod<Int>("next") {
    val amount by param<Int>()
    access { public() }

    code {
        val result by local<Int>()

        count.load()     // FieldRef helper
        load(amount)     // ParamRef
        add()
        store(result)    // LocalRef

        load(result)
        ret()
    }
}
```

## Existing methods

`findMethod` resolves a public Java/Kotlin method or constructor by reflection. Its block declares parameter **types** only, to resolve overloads:

```kotlin
val println = findMethod<java.io.PrintStream, Unit>("println") {
    arg<Int>()
}
```

It finds public candidates and filters by the supplied parameter types. Calling the `MethodRef` still requires the receiver and arguments to be pushed in bytecode order; see [References and operand stack](references-and-stack.md#calls-and-constructors).