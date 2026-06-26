package pt.rafap.klassfile.utils

import pt.rafap.klassfile.builders.CodeScope
import java.io.PrintStream

inline fun <reified T: Any> CodeScope<*, *>.println(
    builder: CodeScope<*, *>.() -> Unit
) {
    getStatic<System, PrintStream>("out")

    builder()

    invokeMethod<PrintStream, Unit>("println") {
        param<T>()
    }
}