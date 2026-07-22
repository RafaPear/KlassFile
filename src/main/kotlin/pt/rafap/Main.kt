package pt.rafap

import pt.rafap.klassfile.utils.klassFile

abstract class ArrayTest {
    abstract val size: Int
    abstract operator fun get(idx: Int): Int
    abstract operator fun set(idx: Int, value: Int)
}

fun buildArr(): ArrayTest {
    val clazz by klassFile<ArrayTest> {

        access { public() }

        val size by field<Int> { public() }
        val array by field<IntArray>()

        constructor {
            access { public() }

            code {
                defaultCtor()

                size.store {
                    ldc(5)
                }

                array.store {
                    size.load()
                    newArray<Int>()
                }
                ret()
            }
        }

        val getSize by getter(size)

        method<Int>("get") {
            access { public() }

            val idx by parameter<Int>()

            code {
                array.load()
                loadRef(idx)
                arrayLoad()
                ret()
            }
        }

        method<Unit>("set") {
            access { public() }

            val idx by parameter<Int>()
            val value by parameter<Int>()

            code {
                array.load()
                loadRef(idx)
                loadRef(value)
                arrayStore()
                ret()
            }
        }
    }

    return clazz.writeAndGetInstance()
}

fun main() {
    val instance = buildArr()

    for (i in 0 until instance.size) {
        instance[i] = i * 10
    }

    for (i in 0 until instance.size) {
        println("instance[$i] = ${instance[i]}")
    }
}