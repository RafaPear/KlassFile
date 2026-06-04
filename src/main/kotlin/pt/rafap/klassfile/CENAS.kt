package pt.rafap.klassfile

import pt.rafap.klassfile.utils.classDesc
import java.io.PrintStream
import java.lang.classfile.ClassFile.ACC_PUBLIC

interface NumberCenas {
	fun printNumber()
}

fun main() {
	KlassFileBuilder.klass("ultra") {
		implements<NumberCenas>()

		val number by field<Int>()

		constructor {
			defaultConstructor()
			aload(0)
			ldc(42)
			putfield(number)
			return_()
		}

		val getNumber by method<Int>(ACC_PUBLIC) {
			getThisField(number)
			ireturn()
		}

		method("printNumber") {
			getstatic<System>("out")

			invokevirtual(getNumber)

			invokevirtual<PrintStream>("println", args = arrayOf(classDesc<Int>()))
			return_()
		}

	}.writeAndGetInstance<NumberCenas>().printNumber()
}