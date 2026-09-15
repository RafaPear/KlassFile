package io.github.rafapear.klassfile.examples

import io.github.rafapear.klassfile.utils.klassFile
import io.github.rafapear.klassfile.utils.println
import kotlin.test.Test

class CounterExample {

    interface Counter {
        fun start(n: Int): Int
    }

    @Test
    fun `Implement an Adder function`() {
        val counter = klassFile<Counter>("CounterImpl") {
            access { public() }

            defineMethod<Int>("start") {
                access { public() }

                val n by param<Int>()

                code {

                    val sum by local<Int>()
                    sum set 0

                    for_(0..n) do_ { i ->
                        println(i)
                        add(i, sum)
                        store(sum)
                    }

                    load(sum)
                    ret()
                }
            }
        }.writeAndGetInstance()

        counter.start(5)
    }
}