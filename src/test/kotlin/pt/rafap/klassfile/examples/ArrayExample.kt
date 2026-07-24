package pt.rafap.klassfile.examples

import pt.rafap.klassfile.utils.klassFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ArrayExample {

    interface Accumulator {
        fun sum(arr: IntArray): Int
    }

    @Test
    fun `Implement a sum interface that sums all numbers in a array`(){
        val accumulator by klassFile<Accumulator> {
            access { public() }

            method<Int>("sum") {
                access { public() }

                val arr by param<IntArray>()

                code {
                    val sum by local<Int>()
                    sum set 0

                    val idx by local<Int>()
                    idx set 0

                    val arrLen by local<Int>()

                    arr.length()
                    store(arrLen)

                    while_ { idx lt arrLen } do_ {
                        load(arr)
                        load(idx)
                        arrayLoad()

                        load(sum)
                        add()
                        store(sum)

                        inc(idx)
                    }

                    load(sum)
                    ret()
                }
            }
        }

        val instance = accumulator.writeAndGetInstance()
        val arr = intArrayOf(1, 2, 3, 4, 5)
        assertEquals(15, instance.sum(arr))
    }
}