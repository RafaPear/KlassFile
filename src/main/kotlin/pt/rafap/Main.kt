package pt.rafap

import pt.rafap.klassfile.utils.klassFile

interface Sum {
    fun sum(arr: IntArray): Int
}

fun buildArr(): Sum {
    val clazz by klassFile<Sum> {
        access { public() }

        method<Int>("sum") {
            access { public() }

            val arr by param<IntArray>()

            code {
                val sum by local<Int>()
                val idx by local<Int>()
                val ref1 by label()
                val ref2 by label()


                ref1.bind()

                ldc(0)
                store(sum)

                goto(ref1)

                load(sum)
                ret()

//                condition {
//                    ldc(1)
//                } then {
//                    println<String> { ldc("0") }
//                } otherwise {
//                    println<String> { ldc("1") }
//                }

            }
        }
    }

    return clazz.writeAndGetInstance()
}

fun main() {
    val instance = buildArr()

    val arr = intArrayOf(1, 2, 3, 4, 5)
    val result = instance.sum(arr)
    println(result)
}