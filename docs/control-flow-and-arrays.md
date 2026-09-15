# Control flow and arrays

## Conditions

Comparison expressions return a `LabelRef`, which can be consumed by `if_` or a loop builder:

```kotlin
if_(value gt 0) {
    ldc("positive")
    ret()
}
```

The DSL supports `eq`, `ne`, `lt`, `le`, `gt`, `ge`, `isNull()`, and `isNonNull()`. Comparisons work with references or integer literals as supported by the underlying JVM comparison instruction.

### Comparison forms

The infix comparison helpers are defined on `OrderedRef`, so their left-hand side must be a parameter or local—not a field. The supported forms are:

```kotlin
left eq right       // OrderedRef vs OrderedRef
left eq 0           // OrderedRef vs Int literal
left lt limit       // OrderedRef vs OrderedRef
left lt 10          // OrderedRef vs Int literal

value.isNull()      // reference parameter/local only
value.isNonNull()
```

`eq` and `ne` accept either two JVM `int`-category values or two references. `lt`, `le`, `gt`, and `ge` accept JVM `int`-category values only; they do not compare `Long`, `Float`, or `Double`. The `int` category includes `Boolean`, `Byte`, `Short`, `Char`, and `Int`.

The comparison creates a `LabelRef` that denotes the **true branch**. `if_(condition) { ... }` consumes that label and executes the body only when the comparison is true:

```kotlin
if_(index lt limit) {
    // emitted only when index < limit
}
```

To compare a field, first put its value in a local, or use low-level field loading and conditional instructions. `if_(count gt 0)` is not available because `count` is a `FieldRef`.

For lower-level control flow, create and bind labels directly:

```kotlin
val end by label()
goto(end)
end.bind()
```

Every jump to the same label must leave a compatible stack shape. KlassFile validates stack size at label targets.

### Low-level branches

Use these when the comparison sugar is insufficient. Every function branches to the supplied `LabelRef` when the test succeeds and consumes its operands:

| API family | Required stack before | Accepted values |
|---|---|---|
| `ifEq`, `ifNe`, `ifLt`, `ifLe`, `ifGt`, `ifGe` | `[int]` | JVM `int` category |
| `ifICmpEq`, `ifICmpNe`, `ifICmpLt`, `ifICmpLe`, `ifICmpGt`, `ifICmpGe` | `[left, right]` | two JVM `int`-category values |
| `ifACmpEq`, `ifACmpNe` | `[left, right]` | two references |
| `ifNull`, `ifNonNull` | `[reference]` | one reference |

`ifCmpEq` and `ifCmpNe` choose the integer or reference variant from the tracked stack types. `ifCmpLt`, `ifCmpLe`, `ifCmpGt`, and `ifCmpGe` only permit integer values. `ifCmpNull` and `ifCmpNonNull` only permit references.

## `while_`

`while_` receives a condition that returns the label to enter the body. Pair it with `do_`:

```kotlin
while_ { index lt limit } do_ {
    inc(index)
}
```

The `condition` lambda must return a `LabelRef` that identifies the body entry when the condition is true. It should normally be one of the comparison expressions above. There is one `while_` form:

```kotlin
while_(condition: CodeScope<O, R>.() -> LabelRef) do_ {
    // body
}
```

The condition is emitted before the body and re-evaluated after every iteration. The body must preserve a stack shape compatible with the condition label. There is no `break` or `continue` convenience API; use explicitly created labels and `goto` for those patterns.

## `for_`

`for_` supports integer ranges and reference-based ranges. The loop body receives the generated index local:

```kotlin
for_(0 until limit) do_ { index ->
    // use index
}
```

The default increment is `inc(index)`. Override it with `incrementor { ... }` before `do_` when needed.

### All `for_` forms

`for_` always produces a `ForRef`, then `do_` emits the loop body. These are the available declarations:

```kotlin
// 1. Provide the index local and a condition.
for_(index) { i -> i lt limit } do_ { i ->
    // index is `i`
}

// 2. Let KlassFile create an Int index initialized to 0.
for_ { i -> i lt limit } do_ { i ->
    // runs while i < limit
}

// 3. Inclusive Kotlin-style range.
for_(0..10) do_ { i ->
    // 0 through 10
}

// 4. Exclusive range with an OrderedRef endpoint.
for_(0 until limit) do_ { i ->
    // 0 through limit - 1
}
```

The range overloads accept these endpoint combinations, where a reference must be `ParamRef<Int>` or `LocalRef<Int>`:

| Range expression | Meaning |
|---|---|
| `start..endRef` | inclusive literal start and reference end |
| `start until endRef` | exclusive literal start and reference end |
| `startRef..end` | inclusive reference start and literal end |
| `startRef until end` | exclusive reference start and literal end |
| `startRef..endRef` | inclusive reference endpoints |
| `startRef until endRef` | exclusive reference endpoints |
| `IntRange` such as `0..10` | inclusive literal endpoints |

`IntRange` cannot express a runtime endpoint. For a runtime endpoint, use `0 until limit` or another `CustomRange` form. `for_` does not accept an `IntProgression` such as `10 downTo 0` or a Kotlin `step`; customize the incrementor instead:

```kotlin
for_(index) { i -> i ge 0 }
    .incrementor { dec(index, 1) }
    .do_ { i ->
        // descending loop
    }
```

The index argument of the first form must be a `LocalRef<Int>`, not a parameter. The default incrementor emits `inc(index)` after the body.

## Arrays

For arrays, load the array and index, then call `arrayLoad()` or `arrayStore()`:

```kotlin
load(values)
load(index)
arrayLoad()
store(result)
```

The indexing operators offer a shorter form for reads and writes:

```kotlin
result set values[index]
values[index] = 10
```

### Accepted array references and stack order

Array indexing operators are defined on `OrderedRef`, so the array must be a parameter or local. A field that holds an array must be loaded explicitly first and then handled with `arrayLoad` or `arrayStore`.

| API | Required stack before (bottom → top) | Stack after |
|---|---|---|
| `arrayLength()` | `[array]` | `[Int]` |
| `arrayLoad()` | `[array, index]` | `[element]` |
| `arrayStore()` | `[array, index, value]` | `[]` |
| `newArray<T>()` | `[Int size]` | `[T array]` |

`arrayLoad` and `arrayStore` require an `Int` index. `arrayStore` checks that the value's tracked type equals the array element type. `newArray<T>()` creates a one-dimensional array; `newMultiArray` exists but is not implemented.

The syntactic forms are:

```kotlin
result set values[0]       // literal index
result set values[index]   // OrderedRef<Int> index
values[0] = 42             // literal index and literal value
values[index] = 42         // OrderedRef<Int> index and literal value
```

The `set` operator currently accepts a literal value, not another reference. To store a referenced value, load the array, index, and value yourself, then call `arrayStore()`.

`arrayLength()` reads the length from an array already on the stack. `array.length()` is a convenience extension for a parameter reference. KlassFile selects the matching JVM array opcode and checks the element type during stores.
