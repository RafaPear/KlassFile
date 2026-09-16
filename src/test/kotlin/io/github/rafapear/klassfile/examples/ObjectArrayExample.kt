package io.github.rafapear.klassfile.examples

import io.github.rafapear.klassfile.utils.klassFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectArrayExample {
    data class Thing(val value: Int)

    interface ObjectStack<T> {
        fun push(item: T)
        fun pop(): T
    }

    inline fun <reified T: Any> createStack(name: String): ObjectStack<T> {
        return klassFile<ObjectStack<T>>(name) {
            access { public() }

            val arr by field<Array<T>>()
            val ptr by field<Int>()

            constructor {
                access { public() }

                code {
                    defaultCtor()
                    putThisField(arr) { ldc(5); newArray<T>() }
                    putThisField(ptr) { ldc(0) }
                    ret()
                }
            }

            defineMethod<Unit>("push") {
                access { public() }

                val item by param<Any>()

                code {
                    // ARR
                    loadRef(arr)
                    // IDX
                    loadRef(ptr)
                    // VALUE
                    load(item)
                    checkCast<T>()
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

            defineMethod<Any>("pop") {
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
    }

    @Test
    fun `Assert a Stack works with custom objects`() {

        val instance = createStack<Thing>("ThingStack")

        instance.push(Thing(10))
        instance.push(Thing(20))
        val popped1 = instance.pop()
        val popped2 = instance.pop()

        println("Popped1: $popped1")
        println("Popped2: $popped2")

        assertEquals(20, popped1.value, "Expected 20, but got $popped1")
        assertEquals(10, popped2.value, "Expected 10, but got $popped2")
    }

    @Test
    fun `Assert a Stack works with Strings (example)`() {

        val instance = createStack<String>("StringStack")

        instance.push("str1")
        instance.push("str2")
        val popped1 = instance.pop()
        val popped2 = instance.pop()

        println("Popped1: $popped1")
        println("Popped2: $popped2")

        assertEquals("str2", popped1, "Expected \"str1\", but got $popped1")
        assertEquals("str1", popped2, "Expected \"str2\", but got $popped2")
    }
}