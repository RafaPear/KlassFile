# Types and member references

## Reified Kotlin types

Most declaration APIs accept a reified type parameter:

```kotlin
field<Int>()
defineMethod<String>("message") { /* ... */ }
param<IntArray>()
local<Long>()
```

KlassFile converts these types to its `KlassDesc<T>` representation and then to Java's `ClassDesc`. This avoids manually typing descriptors such as `I` or `Ljava/lang/String;` for these common cases.

## `KlassDesc`

Use `klassDescOf<T>()` when an API needs an explicit type value:

```kotlin
import pt.rafap.klassfile.utils.klassDescOf

val stringType = klassDescOf<String>()
val namesType = klassDescOf<String>().array()
```

`KlassDesc` couples a JVM `ClassDesc` and a Kotlin `KClass`. It is also available through constructors that accept a `KClass`, `Class`, or `KType`.

## Arrays and type limits

Primitive arrays, such as `IntArray`, are supported. Object arrays can be represented with `Array<String>`. `Array<Int>` and similar arrays of Kotlin primitive types are rejected deliberately because their JVM representation (`Integer[]`) differs from a primitive array (`int[]`).

The reified DSL APIs use `T : Any`; nullable types are therefore not accepted by those APIs. Kotlin generic type arguments are erased on the JVM, so `List<String>` and `List<Int>` have the same runtime class descriptor.

## References

The DSL returns references that describe declared members and values:

- `FieldRef` for fields.
- `MethodRef` for methods and constructors.
- `ParamRef` for parameters.
- `LocalRef` for local variables.

Keep these values and use them in code generation. This preserves the owner and type information needed by instructions:

```kotlin
val value by field<Int>()
val getValue by getter(value)

defineMethod<Int>("read") {
    access { public() }
    code {
        loadReceiver()
        invokeMethod(getValue)
        ret()
    }
}
```

### Which references can an instruction accept?

The distinction matters because the DSL does not accept every reference type everywhere.

| Reference type    | Created by                             | Represents                          | Accepted directly by                                                           |
|-------------------|----------------------------------------|-------------------------------------|--------------------------------------------------------------------------------|
| `ParamRef<T>`     | `val value by param<T>()`              | A method parameter                  | `load`, `store`, `inc`, `dec`, comparisons, array indexing, arithmetic helpers |
| `LocalRef<T>`     | `val total by local<T>()`              | A local variable slot               | `load`, `store`, `inc`, `dec`, comparisons, array indexing, arithmetic helpers |
| `FieldRef<O, T>`  | `field`, `defineField`                 | A declared instance or static field | field helpers; also arithmetic helpers through `TypedRef`                      |
| `MethodRef<O, R>` | `method`, `defineMethod`, `findMethod` | A callable method or constructor    | `invokeMethod` and explicit invocation helpers                                 |
| `LabelRef`        | `val end by label()`                   | A branch target                     | `goto`, low-level conditional instructions, `if_`                              |

`ParamRef` and `LocalRef` implement `OrderedRef`; `FieldRef` does not. Therefore `load(field)` is not valid. Use `field.load()`, or explicitly load the receiver and call `getField(field)`. Likewise, range, comparison, and array-indexing sugar is defined for `OrderedRef` values, not fields.

The arithmetic convenience overloads (`add`, `sub`, `mul`, `div`, `rem`, `neg`, shifts, and bitwise operations) accept `TypedRef`, so they can load a parameter, local, or field. When passed a field, they load the current receiver and read that field.

## Resolving existing methods

`findMethod` resolves a public Java/Kotlin method through reflection. Give the owner, return type, and parameter types to disambiguate overloads:

```kotlin
val println by findMethod<java.io.PrintStream, Unit>("println") {
    arg<Int>()
}
```

The returned `MethodRef` can be passed to `invokeMethod`. Resolution considers public members; an ambiguous overload causes an error with the remaining candidates.
