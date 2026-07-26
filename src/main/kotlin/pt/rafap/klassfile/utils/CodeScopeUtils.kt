package pt.rafap.klassfile.utils

import pt.rafap.klassfile.builders.CodeScope
import pt.rafap.klassfile.models.OrderedRef
import java.io.PrintStream

inline fun <reified T: Any> CodeScope<*, *>.println(
    builder: CodeScope<*, *>.() -> Unit
) {
    getStatic<System, PrintStream>("out")

    builder()

    invokeMethod<PrintStream, Unit>("println") {
        arg<T>()
    }
}

//inline fun <reified T: Any> CodeScope<*, *>.println(ref: OrderedRef<T>) {
//    getStatic<System, PrintStream>("out")
//
//    load(ref)
//
//    invokeMethod<PrintStream, Unit>("println") {
//        arg<T>()
//    }
//}

fun <T: Any> CodeScope<*, *>.println(ref: OrderedRef<T>) {
    getStatic<System, PrintStream>("out")

    load(ref)

    invokeMethod<PrintStream, Unit>("println") {
        arg(ref.type)
    }
}