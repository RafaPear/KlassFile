package pt.rafap.klassfile.examples

import pt.rafap.klassfile.KlassFileBuilder
import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.utils.klassFile
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorExample {
    abstract class Calculator {
        abstract fun getSum(): Int

        abstract fun add(a: Int): Int
        abstract fun sub(a: Int): Int
        abstract fun mul(a: Int): Int
        abstract fun div(a: Int): Int

        fun printSum() {
            println("Sum: ${getSum()}")
        }
    }

    fun KlassFileBuilder<Calculator>.implementOperation(
        name: String,
        getSum: MethodRef<Calculator, Int>,
        sum: FieldRef<Calculator, Int>,
        op: CodeScope<*, *>.() -> Unit
    ) {
        method<Int>(name) {
            access { public() }

            val a by param<Int>()

            code {
                loadReceiver() // Loads receiver to load the field
                getSum() // Get Sum Value
                load(a) // Get Param Value
                op() // calls the operator
                store(a) // stores the sum back in a for ease

                putThisField(sum) { load(a) } // Store the new sum value in the field
                load(a) // Load the new sum value to return it

                ret() // Return the new sum value
            }
        }
    }

    @Test
    fun `Implement a Calculator interface that sums all numbers`() {
        val calculator by klassFile<Calculator> {
            access { public() }

            val sum by field<Int>()
            val getSum by getter(sum)

            constructor {
                access { public() }

                code {
                    defaultCtor()
                    putThisField(sum) { ldc(0) }
                    ret()
                }
            }

            implementOperation("add", getSum, sum) {
                add()
            }

            implementOperation("sub", getSum, sum) {
                sub()
            }

            implementOperation("mul", getSum, sum) {
                mul()
            }

            implementOperation("div", getSum, sum) {
                div()
            }
        }

        val instance = calculator.writeAndGetInstance()
        assertEquals(0, instance.getSum())
        assertEquals(5, instance.add(5))
        assertEquals(8, instance.add(3))
        assertEquals(6, instance.sub(2))
        assertEquals(12, instance.mul(2))
        assertEquals(6, instance.div(2))
        instance.printSum()
    }
}