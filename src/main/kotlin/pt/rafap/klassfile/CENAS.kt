package pt.rafap.klassfile

import java.io.PrintStream

fun main() {
    val klass = KlassFileBuilder.klass<Runnable>("Stress") {

        access { public() }

        val intField by field<Int> { public() }
        val strField by field<String> { private() }

        val getInt by getter(intField)
        val setInt by setter(intField)

        constructor {
            access { public() }

            code {
                defaultCtor()

                load(receiver)
                ldc(123)
                setInt()

                ret()
            }
        }

        method<Unit>("run") {
            access { public() }

            code {
                getStatic<System, PrintStream>("out")

                load(receiver)
                getInt()

                val println by findMethod<PrintStream, Unit> {
                    param<Int>()
                }

                invokeMethod(println)

                ret()
            }
        }

        method<Int>("math") {
            val a by parameter<Int>()
            val b by parameter<Int>()

            access { public() }

            code {
                a + b
                ret()
            }
        }

        method<String>("string") {
            access { public() }

            code {
                ldc("Hello")
                ret()
            }
        }

        method<Long>("longValue") {
            access { public() }

            code {
                ldc(42L)
                ret()
            }
        }

        method<Double>("doubleValue") {
            access { public() }

            code {
                ldc(3.14)
                ret()
            }
        }

        method<Float>("floatValue") {
            access { public() }

            code {
                ldc(2.5f)
                ret()
            }
        }
    }.writeAndGetInstance().run()
}