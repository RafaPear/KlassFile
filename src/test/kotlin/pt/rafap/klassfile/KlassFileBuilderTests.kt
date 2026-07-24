package pt.rafap.klassfile

import pt.rafap.klassfile.utils.klassFile
import kotlin.test.Test
import kotlin.test.assertEquals

class KlassFileBuilderTests {

    interface Sum {
        fun sum(arr: IntArray): Int
        fun ge(a: Int, b: Int): Boolean
    }

    fun buildArr(): Sum {
        val clazz by klassFile<Sum> {
            access { public() }

            method<Boolean>("ge") {
                access { public() }

                val a by param<Int>()
                val b by param<Int>()

                code {
                    // a >= b
                    if_(a ge 0) {
                        ldc(0)
                        ret()
                    }
                    ldc(1)
                    ret()
                }
            }

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

        return clazz.writeAndGetInstance()
    }

    @Test
            /** Verifies that the KlassFile builder test scaffold is wired correctly. */
    fun `build KlassFile builder`() {
        val instance = buildArr()
        val arr = intArrayOf(1, 2, 3, 4, 5)
        assertEquals(15, instance.sum(arr))
    }
}