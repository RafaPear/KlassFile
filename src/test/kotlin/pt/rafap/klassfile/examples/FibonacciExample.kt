package pt.rafap.klassfile.examples

import pt.rafap.klassfile.utils.klassFile
import kotlin.test.Test
import kotlin.test.assertEquals


class FibonacciExample {
    interface Fibonacci {
        fun fib(n: Int): Int
    }

    @Test
    fun `Implement a Fibonacci function`() {
        val fibonacci = klassFile<Fibonacci>("FibonacciImpl") {
            access { public() }

            method<Int>("fib") {
                access { public() }

                val n by param<Int>()

                code {
                    // if (n <= 1) return n;
                    if_(n le 1) {
                        load(n)
                        ret()
                    }

                    val tmp1 by local<Int>()
                    tmp1 set 0
                    val tmp2 by local<Int>()
                    tmp2 set 1
                    val i by local<Int>()
                    i set 2

                    while_ { i le n } do_ {
                        add(tmp1, tmp2) // tmp1 + tmp2

                        load(tmp2)
                        store(tmp1) // tmp1 = tmp2

                        store(tmp2) // tmp2 = tmp1 + tmp2

                        inc(i)
                    }

                    load(tmp2)
                    ret()
                }
            }

        }.writeAndGetInstance()

        assertEquals(fibonacci.fib(0), 0)
        assertEquals(fibonacci.fib(1), 1)
        assertEquals(fibonacci.fib(2), 1)
        assertEquals(fibonacci.fib(3), 2)
        assertEquals(fibonacci.fib(4), 3)
        assertEquals(fibonacci.fib(5), 5)
        assertEquals(fibonacci.fib(6), 8)
    }
}