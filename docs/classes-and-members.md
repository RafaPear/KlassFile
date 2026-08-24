# Classes, fields, and methods

## Class access

Use `access` at the class level to set class-file flags.

```kotlin
klassFile<MyInterface>("example.MyImplementation") {
    access {
        public()
        final()
    }
}
```

Class access supports `public`, `protected`, `private`, `final`, `abstract`, `super_`, and `synthetic`. In normal use, generated classes should be `public`.

The generated class either implements the interface passed as `T`, or extends the abstract class passed as `T`. Every abstract method visible on that type must be implemented before the class is built.

## Fields

Declare a field with a delegated property. Its Kotlin property name becomes the JVM field name.

```kotlin
val count by field<Int> { private() }
```

For a specific name or an explicit descriptor, use `defineField`:

```kotlin
val count = defineField("count", klassDescOf<Int>()) { private() }
```

Field access supports `public`, `protected`, `private`, `static`, `final`, `volatile`, `transient`, `synthetic`, and `enum`. Fields are private by default.

## Getters and setters

`getter` and `setter` generate conventional Java-style methods from a `FieldRef`:

```kotlin
val age by field<Int> { private() }
val getAge by getter(age)       // getAge(): Int
val setAge by setter(age)       // setAge(age: Int): Unit
```

The generated accessors are public by default. Pass an access block to change that. For a non-standard name, use `defineGetter("readAge", age)` or `defineSetter("changeAge", age)`.

## Methods and parameters

Use `defineMethod<R>(name)` for a named method, or `method<R>` when a delegated property should determine the method name.

```kotlin
val add by method<Int> {
    val value by param<Int>()

    access { public() }
    code {
        load(value)
        ldc(1)
        add()
        ret()
    }
}
```

Method parameters must be declared before `code { ... }`. A method must emit `ret()` on every generated execution path that reaches the end of the code scope.

Method flags include `public`, `protected`, `private`, `static`, `final`, `synchronized`, `bridge`, `varargs`, `native`, `abstract`, `strict`, and `synthetic`. The invocation kind is derived from the resulting flags and method name.

## Constructors

Declare a constructor explicitly when it needs custom initialization:

```kotlin
constructor {
    access { public() }
    code {
        defaultCtor()
        putThisField(count) { ldc(0) }
        ret()
    }
}
```

`defaultCtor()` loads the receiver and invokes the appropriate superclass constructor. Constructors use `Unit` as their return type and are emitted as `<init>` methods.

