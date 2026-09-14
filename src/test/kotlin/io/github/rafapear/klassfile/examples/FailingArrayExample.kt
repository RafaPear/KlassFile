package io.github.rafapear.klassfile.examples

import io.github.rafapear.klassfile.utils.UnsupportedKotlinArrayOfPrimitivesError
import io.github.rafapear.klassfile.utils.klassFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FailingArrayExample {
    interface Stack {
        fun push(item: Int)
        fun pop(): Int
    }

    @Test
    fun `Assert a Array of primitives fails`() {
        assertFailsWith<UnsupportedKotlinArrayOfPrimitivesError> {
            klassFile<Stack>("FailIntStack") {
                access { public() }

                defineField<Array<Int>>("arr")
            }
        }
    }

    @Test
    fun `Assert a IntArray works`() {

        val instance = klassFile<Stack>("IntStack") {
            access { public() }

            val arr by field<IntArray>()
            val ptr by field<Int>()

            constructor {
                access { public() }

                code {
                    defaultCtor()
                    putThisField(arr) { ldc(5); newArray<Int>() }
                    putThisField(ptr) { ldc(0) }
                    ret()
                }
            }

            defineMethod<Unit>("push") {
                access { public() }

                val item by param<Int>()

                code {
                    // ARR
                    loadRef(arr)
                    // IDX
                    loadRef(ptr)
                    // VALUE
                    load(item)
                    // Store the value in the array at the index
                    arrayStore()

                    putThisField(ptr) {
                        loadRef(ptr)
                        ldc(1)
                        add()
                    }

                    ret()
                }
            }

            defineMethod<Int>("pop") {
                access { public() }

                code {
                    putThisField(ptr) {
                        loadRef(ptr)
                        ldc(1)
                        sub()
                    }

                    // ARR
                    loadRef(arr)
                    // IDX
                    loadRef(ptr)
                    // Store the value in the array at the index
                    arrayLoad()

                    ret()
                }
            }
        }.writeAndGetInstance()

        instance.push(10)
        instance.push(20)
        val popped1 = instance.pop()
        val popped2 = instance.pop()

        assertEquals(20, popped1, "Expected 20, but got $popped1")
        assertEquals(10, popped2, "Expected 10, but got $popped2")
    }
}