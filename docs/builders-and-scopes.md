# Builders and scopes

KlassFile is a nested Kotlin DSL. Every block changes the receiver—the object whose functions are available inside that block. This is why `field`, `param`, and `ldc` are available in different places.

```text
klassFile<T>(name) {                    KlassFileBuilder<T>
    access { ... }                       ClassFlagsScope
    field<Int> { ... }                   FieldFlagsScope

    defineMethod<Int>("sum") {          MethodScope<T, Int>
        param<Int>()
        access { ... }                   MethodFlagsScope

        code {                           CodeScope<T, Int>
            local<Int>()
            ldc(1)
            ret()
        }
    }
}
```

## Scope overview

| Scope                 | Entered through                             | Responsibility                                               | Common APIs                                                                                                                           | Not available here                      |
|-----------------------|---------------------------------------------|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------|
| `KlassFileBuilder<T>` | `klassFile<T>(name) { ... }`                | Describes the generated class                                | `access`, `field`, `defineField`, `method`, `defineMethod`, `constructor`, `getter`, `setter`                                         | `param`, `local`, bytecode instructions |
| `ClassFlagsScope`     | Class-level `access { ... }`                | Class access flags                                           | `public`, `private`, `protected`, `final`, `abstract`, `super_`, `synthetic`                                                          | Members and bytecode instructions       |
| `FieldFlagsScope`     | The block passed to `field` / `defineField` | Field access flags                                           | `public`, `private`, `protected`, `static`, `final`, `volatile`, `transient`, `synthetic`, `enum`                                     | Method/code instructions                |
| `MethodScope<O, R>`   | `method`, `defineMethod`, `constructor`     | Method signature, flags, and body                            | `param`, `defineParam`, `receiver`, `access`, `code`                                                                                  | Bytecode instructions directly          |
| `MethodFlagsScope`    | Method-level `access { ... }`               | Method access flags                                          | `public`, `private`, `protected`, `static`, `final`, `synchronized`, `bridge`, `varargs`, `native`, `abstract`, `strict`, `synthetic` | Parameters and bytecode instructions    |
| `ArgumentScope`       | `findMethod { ... }`, `instantiate { ... }` | Describes parameter types for reflection overload resolution | `arg<T>()`, `arg(klassDesc)`                                                                                                          | Loading values or emitting bytecode     |
| `CodeScope<O, R>`     | `code { ... }`                              | Emits a method body                                          | locals, instructions, fields, calls, branches, arrays, stack operations                                                               | Method declaration and flags            |

`O` is the generated class type and `R` is the method return type. `CodeScope<O, R>` therefore knows the method's owner, declared result type, parameters, locals, and tracked operand stack.

## `KlassFileBuilder`: class declaration scope

This is the outermost scope. It only defines class structure; it does not emit bytecode itself.

```kotlin
klassFile<Counter>("example.CounterImpl") {
    access { public() }

    val count by field<Int> { private() }
    val readCount by getter(count)

    defineMethod<Int>("increment") {
        // MethodScope begins here.
    }
}
```

Delegated declarations capture their Kotlin property name:

```kotlin
val count by field<Int>()       // JVM field: count
val increment by method<Unit> { /* ... */ } // JVM method: increment
```

Use `defineField("name", ...)` and `defineMethod("name", ...)` when the JVM name must be explicit.

## `MethodScope`: signature before bytecode

Inside a method builder, declare parameters and flags first, then enter `code`:

```kotlin
defineMethod<Int>("add") {
    val amount by param<Int>()
    access { public() }

    code {
        // CodeScope starts here.
        load(amount)
        ret()
    }
}
```

`param<T>()` returns an eager delegate, so the property name becomes the parameter name used in diagnostics. `defineParam(name, type)` is the explicit alternative. Once `code` has started, parameter declaration is rejected with `ParamDefinitionInCodeError`.

`receiver()` provides a `ParamRef.ReceiverRef<O>` for the implicit `this` parameter. Most code should call `loadReceiver()` instead, because it both loads that reference and updates the operand-stack model.

The method `access` block configures a `MethodFlagsScope`; it is not the class-level `access` block even though the syntax looks the same.

## `ArgumentScope`: types, not values

`ArgumentScope` is deliberately not a code-emission scope. It supplies parameter types to reflection-based overload resolution.

```kotlin
val append = findMethod<StringBuilder, StringBuilder>("append") {
    arg<String>()
}

load(builder)
ldc("text")
invokeMethod(append)
```

The `arg<String>()` call does **not** push a string. It only says “resolve the overload whose parameter is `String`.” The receiver and actual argument values are still loaded in the surrounding `CodeScope`.

The same rule applies to `instantiate<T> { arg<X>() }`: `arg<X>()` chooses the constructor overload; you must push the constructor arguments before `instantiate`.

## `CodeScope`: bytecode emission

`code { ... }` enters `CodeScope`, which is the only scope that emits instructions. It owns:

- Method parameters and the implicit receiver.
- Locals declared through `local<T>()` / `defineLocal`.
- The sequence of emitted JVM instructions.
- The simulated operand stack used for validation.

```kotlin
code {
    val total by local<Int>()
    total set 0

    load(total)
    ldc(1)
    add()
    store(total)

    load(total)
    ret()
}
```

`code` may be called to continue emitting into the same method scope, but it cannot be nested inside another active `code` call. Each finished method needs at least one `ret()` and no remaining tracked stack values.

## Flag scopes

The three flag scopes share one validation rule: select exactly one visibility modifier. Their non-visibility flags differ because JVM classes, methods, and fields have different legal flag sets.

```kotlin
access { public() }             // ClassFlagsScope or MethodFlagsScope
field<Int> { private(); final() } // FieldFlagsScope
```

The receiver type is inferred from the surrounding builder. Kotlin's `@DslMarker` annotations prevent accidentally calling APIs from an unrelated outer DSL scope.

