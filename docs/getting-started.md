# Getting started

KlassFile creates a concrete implementation of an interface or abstract class. The type argument to `klassFile<T>` is the type the generated class implements or extends; `name` is the generated class's binary name.

```kotlin
import io.github.rafapear.klassfile.utils.klassFile

interface Greeter {
    fun greet(): String
}

val greeter = klassFile<Greeter>("example.GreeterImpl") {
    access { public() }

    defineMethod<String>("greet") {
        access { public() }
        code {
            ldc("Hello from KlassFile")
            ret()
        }
    }
}.writeAndGetInstance()

println(greeter.greet())
```

`writeAndGetInstance()` builds the byte array, writes it under the generated binary name, loads it through KlassFile's class loader, and creates an instance through a public no-argument constructor. When no constructor is declared, KlassFile adds a default public no-argument constructor.

## Build without instantiating

The terminal `klassFile` call returns a `KlassFileBuilder.Klass` value. It exposes:

- `bytes` — the generated class-file bytes.
- `writeToDisk()` — writes the `.class` file.
- `writeAndGetClass()` — writes and loads the class.
- `writeAndGetInstance()` — writes, loads, and creates an instance.

Use `writeAndGetClass()` for types without an accessible no-argument constructor, or when instantiation should be controlled by your application.

## Generated class names

Pass a binary name such as `"example.GreeterImpl"`. KlassFile writes it as `example/GreeterImpl.class` under its runtime output path.

## Build from source

This repository does not currently publish a Maven dependency. Build it from source with the Gradle wrapper:

```shell
./gradlew build
```

The project uses a Java 24 toolchain. Add the resulting artifact to a consuming project according to your local build setup.

