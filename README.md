# KlassFile

> ⚠️ WORK IN PROGRESS ⚠️

## Status

| Feature                                    | Status                                             |
|--------------------------------------------|----------------------------------------------------|
| Class Generation                           | ✅ Complete                                         |
| Field Generation                           | ✅ Complete                                         |
| Method Generation                          | ✅ Complete                                         |
| Constructor Generation                     | ✅ Complete                                         |
| Getter / Setter Generation                 | ✅ Complete                                         |
| Runtime Class Loading                      | ✅ Complete                                         |
| JVM Stack Verification                     | ✅ Complete                                         |
| Method Resolution (Reflection)             | ✅ Complete                                         |
| Reflection-based Method References         | ✅ Complete                                         |
| Automatic Access Flag Validation           | ✅ Complete                                         |
| Bytecode DSL                               | 🚧 In Progress                                     |
| JVM Instruction Set                        | 🚧 Incomplete                                      |
| Inheritance                                | 🚧 Supports either one superclass or one interface |
| Control Flow (`if`, `goto`, loops, labels) | ❌ Not implemented                                  |
| Exception Handling (`try` / `catch`)       | ❌ Not implemented                                  |
| Local Variables API                        | 🚧 Basic support                                   |
| Stack Map Frames                           | ❌ Not implemented                                  |
| Generics Metadata                          | ❌ Not implemented                                  |
| Annotations                                | ❌ Not implemented                                  |
| Invokedynamic / Lambdas                    | ❌ Not implemented                                  |

# Example

```kotlin
interface Counter {

    // Increments the current counter by one.
    fun increment()

    // Adds the given value to the counter.
    fun addValue(value: Int)

    // Resets the counter to zero.
    fun reset()

    // Returns the current counter value.
    fun get(): Int

    // Prints the current counter value.
    fun print()
}

fun main() {

    // Generate a concrete implementation of the Counter interface.
    val counter = klassFile<Counter>("CounterImpl") {

        // Configure the generated class.
        access { public() }

        // Field name is defined by the property name.
        val value by field<Int>()

        // Automatically generate a private getter and setter.
        val getValue by getter(value) { private() }
        val setValue by setter(value) { private() }

        constructor {
            access { public() }

            code {
                // Call Object.<init>()
                defaultCtor()

                // value = 0
                loadReceiver() // Every MethodScope contains an implicit receiver
                ldc(0)
                setValue()

                ret()
            }
        }

        // Generates a method with parameters.
        val addValue by method<Unit> {

            // Declare JVM method parameters.
            val amount by parameter<Int>()

            access { public() }

            code {
                // value += amount
                loadReceiver()
                add(value, amount)
                setValue()

                ret()
            }
        }

        method<Unit>("increment") {

            access { public() }

            code {
                loadReceiver()
                ldc(1)

                // Invoke another generated method through its MethodRef.
                addValue()

                ret()
            }
        }

        method<Unit>("reset") {

            access { public() }

            code {
                loadReceiver()
                ldc(0)
                setValue()

                ret()
            }
        }

        method<Int>("get") {

            access { public() }

            code {
                loadReceiver()
                getValue()

                ret()
            }
        }

        method<Unit>("print") {

            access { public() }

            code {

                // Access a static JVM field.
                getStatic<System, PrintStream>("out")

                loadReceiver()
                getValue()

                // Resolve an existing JVM method using reflection.
                val println by findMethod<PrintStream, Unit> {
                    param<Int>()
                }

                // Invoke the resolved MethodRef.
                println()

                ret()
            }
        }

    }.writeAndGetInstance() // Build, load and instantiate the generated class.

    counter.increment()
    counter.increment()
    counter.addValue(10)

    println(counter.get()) // 12

    counter.print()

    counter.reset()

    println(counter.get()) // 0
}
```
