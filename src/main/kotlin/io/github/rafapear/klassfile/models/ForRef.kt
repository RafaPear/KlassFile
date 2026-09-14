package io.github.rafapear.klassfile.models

import io.github.rafapear.klassfile.builders.CodeScope

data class ForRef<O : Any, R : Any>(
    val condLabel: LabelRef,
    val bodyLabel: LabelRef,
    val breakLabel: LabelRef,
    internal val idx: LocalRef<Int>,
    val incrementor: CodeScope<O, R>.() -> Unit = { inc(idx) },
)