package pt.rafap.klassfile.examples

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.models.LocalRef
import pt.rafap.klassfile.utils.klassFile
import kotlin.random.Random
import kotlin.test.Test

class BubbleSortExample {

    interface IntSorter {
        fun sort(arr: IntArray): IntArray
    }

    fun CodeScope<*, *>.for_i(
        n: LocalRef<Int>,
        body: CodeScope<*, *>.(LocalRef<Int>) -> Unit,
    ) {
        val tmpI by local<Int>()
        tmpI set n
        inc(tmpI, -1) // n - 1

        for_(0 until tmpI) do_ (body)
    }

    fun CodeScope<*, *>.for_j(
        n: LocalRef<Int>,
        i: LocalRef<Int>,
        body: CodeScope<*, *>.(LocalRef<Int>) -> Unit,
    ) {
        val tmpJ by local<Int>()
        tmpJ set n
        sub(tmpJ, i) // n - i
        inc(tmpJ, -1) // n - i - 1
        store(tmpJ)

        for_(0 until tmpJ) do_ (body)
    }

    @Test
    fun `Implement a Bubble Sort function`() {
        val intSorter by klassFile<IntSorter> {
            access { public() }

            method<IntArray>("sort") {
                access { public() }

                val arr by param<IntArray>()

                code {
                    val n by local<Int>()
                    n set arr.length()

                    val tmp by local<Int>()

                    for_i(n) { i ->
                        val breakLoop by label()
                        for_j(n, i) { j ->

                            tmp set j
                            inc(tmp) // tmp = j + 1
                            if_(tmp ge n) {
                                goto(breakLoop)
                            }

                            tmp set arr[tmp] // arr[j + 1]
                            if_(arr[j] gt tmp) {
                                // Swap arr[j] and arr[j + 1]
                                // tmp = arr[j + 1]
                                // arr[j+1] = arr[j]
                                // arr[j] = tmp

                                load(arr) // ARR

                                load(j)
                                ldc(1)
                                add() // j + 1 IDX

                                load(arr)
                                load(j)
                                arrayLoad() // arr[j] VALUE

                                arrayStore() // arr[j + 1] = arr[j]

                                load(arr)
                                load(j)
                                load(tmp)
                                arrayStore() // arr[j] = tmp
                            }
                        }
                        breakLoop.bind()
                    }
                    load(arr)
                    ret()
                }
            }
        }

        val instance = intSorter.writeAndGetInstance()
        val input = IntArray(10) { Random.nextInt(0, 100) }
        println("Input array: ${input.joinToString(", ")}")
        val sorted = instance.sort(input)
        println("Sorted array: ${sorted.joinToString(", ")}")
        assert(sorted.size == input.size)
        assert(input.sorted() == sorted.toList())
    }
}