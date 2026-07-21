package pt.rafap

import pt.rafap.klassfile.utils.klassFile
import java.io.PrintStream

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
    val counter by klassFile<Counter> {

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
                ldc(0)

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

    }
    val instance = counter.writeAndGetInstance() // Build, load and instantiate the generated class.

    instance.increment()
    instance.increment()
    instance.addValue(10)

    println(instance.get()) // 12

    instance.print()

    instance.reset()

    println(instance.get()) // 0
}