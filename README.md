# KlassFile

[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=RafaPear_KlassFile)](https://sonarcloud.io/summary/new_code?id=RafaPear_KlassFile)
> ⚠️ WORK IN PROGRESS ⚠️

## Status

| Feature                                      | Status                                             |
|----------------------------------------------|----------------------------------------------------|
| Class Generation                             | ✅ Complete                                         |
| Field Generation                             | ✅ Complete                                         |
| Method Generation                            | ✅ Complete                                         |
| Constructor Generation                       | ✅ Complete                                         |
| Getter / Setter Generation                   | ✅ Complete                                         |
| Runtime Class Loading                        | ✅ Complete                                         |
| JVM Stack Verification                       | ✅ Complete                                         |
| Method Resolution (Reflection)               | ✅ Complete                                         |
| Reflection-based Method References           | ✅ Complete                                         |
| Automatic Access Flag Validation             | ✅ Complete                                         |
| Inheritance Method Implementation Validation | ✅ Complete                                         |
| Inheritance                                  | 🚧 Supports either one superclass or one interface |
| Bytecode DSL                                 | 🚧 In Progress                                     |
| JVM Instruction Set                          | 🚧 Incomplete                                      |
| Control Flow (`if`, `goto`, loops, labels)   | ✅ Complete                                         |
| Exception Handling (`try` / `catch`)         | ❌ Not implemented                                  |
| Local Variables API                          | ✅ Complete                                         |
| Invokedynamic / Lambdas                      | ❌ Not Planed                                       |

## Test Suite Status

| Test Suite       | Status     |
|------------------|------------|
| Argument Scope   | ✅ Complete |
| Field Scope      | ✅ Complete |
| Flags Scope      | ✅ Complete |
| Method Scope     | ⏳ Pending  |
| Code Scope       | ⏳ Pending  |
| Class Scope      | ⏳ Pending  |
| Locals Storage   | ⏳ Pending  |
| Stack            | ⏳ Pending  |
| LabelRef         | ⏳ Pending  |
| WhileRef         | ⏳ Pending  |
| LocalRef         | ⏳ Pending  |
| ParamRef         | ⏳ Pending  |
| OrderedRef       | ⏳ Pending  |
| TypedRef         | ⏳ Pending  |
| FieldRef         | ⏳ Pending  |
| MethodRef        | ⏳ Pending  |
| KlassDesc        | ⏳ Pending  |
| Invoke Type      | ⏳ Pending  |
| Stack Type       | ⏳ Pending  |
| Stack Value      | ⏳ Pending  |
| Instruction      | ⏳ Pending  |
| Method Resolver  | ⏳ Pending  |
| ClassDesc Utils  | ⏳ Pending  |
| CodeScope Utils  | ⏳ Pending  |
| Eager Delegate   | ⏳ Pending  |
| KlassFile Errors | ⏳ Pending  |
| KlassFile Utils  | ⏳ Pending  |
| MethodRef Utils  | ⏳ Pending  |

# Example

```kotlin
interface Counter {

    // Increments the current counter by one.
    fun increment()

    // Adds the given value to the counter.
    fun addNumber(value: Int)

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
        val numberField by field<Int>()

        // Automatically generate a private getter and setter.
        val getNumber by getter(numberField) { private() }
        val setNumber by setter(numberField) { private() }

        constructor {
            access { public() }

            code {
                // Call Object.<init>()
                defaultCtor()

                // value = 0
                loadReceiver() // Every MethodScope contains an implicit receiver
                ldc(0)
                invokeMethod(setNumber)

                ret()
            }
        }

        // Generates a method with parameters.
        val addNumber by method<Unit> {

            // Declare JVM method parameters.
            val amount by param<Int>()

            access { public() }

            code {
                // value += amount
                loadReceiver()
                add(numberField, amount)
                invokeMethod(setNumber)

                ret()
            }
        }

        method<Unit>("increment") {

            access { public() }

            code {
                loadReceiver()
                ldc(1)

                // Invoke another generated method through its MethodRef.
                invokeMethod(addNumber)

                ret()
            }
        }

        method<Unit>("reset") {

            access { public() }

            code {
                loadReceiver()
                ldc(0)
                invokeMethod(setNumber)

                ret()
            }
        }

        method<Int>("get") {

            access { public() }

            code {
                loadReceiver()
                invokeMethod(getNumber)

                ret()
            }
        }

        method<Unit>("print") {

            access { public() }

            code {

                // Access a static JVM field.
                getStatic<System, PrintStream>("out")

                loadReceiver()
                invokeMethod(getNumber)

                // Resolve an existing JVM method using reflection.
                val println by findMethod<PrintStream, Unit> {
                    param<Int>()
                }

                // Invoke the resolved MethodRef.
                invokeMethod(println)

                ret()
            }
        }

    }.writeAndGetInstance() // Build, load and instantiate the generated class.

    counter.increment()
    counter.increment()
    counter.addNumber(10)

    println(counter.get()) // 12

    counter.print()

    counter.reset()

    println(counter.get()) // 0
}
```
