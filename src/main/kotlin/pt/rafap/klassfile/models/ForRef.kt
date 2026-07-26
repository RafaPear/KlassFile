package pt.rafap.klassfile.models

import pt.rafap.klassfile.builders.CodeScope

data class ForRef<O : Any, R : Any>(
    val condLabel: LabelRef,
    val bodyLabel: LabelRef,
    val breakLabel: LabelRef,
    internal val idx: LocalRef<Int>,
    val incrementor: CodeScope<O, R>.() -> Unit = { inc(idx) },
)