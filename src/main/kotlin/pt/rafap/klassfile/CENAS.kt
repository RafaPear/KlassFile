package pt.rafap.klassfile

import pt.rafap.klassfile.utils.classDesc
import java.io.PrintStream

interface NumberCenas {
    fun printNumber()
}

// TODO:
// method<Int>() {
//    val a by parameter<Int>() // ISTO
//    val b by parameter<Int>()
//
//    code { // ISTO
//        iload(a)
//        iload(b)
//        iadd()
//        ireturn()
//    }
//}

fun main() {
    KlassFileBuilder.klass("ultra") {
        inherit<NumberCenas>()

        access {
            public()
        }

        val number by field<Int>()

        val getNumber by getter(number)
        val setNumber by setter(number)

        constructor {
            defaultConstructor()
            aload(0)
            ldc(42)
            setNumber()
            return_()
        }

        method("printNumber") {
            getstatic<System>("out")

            aload(0)
            getNumber()

            invokevirtual<PrintStream>("println", args = arrayOf(classDesc<Int>()))
            return_()
        }

    }.writeAndGetInstance<NumberCenas>().printNumber()
}