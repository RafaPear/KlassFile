package pt.rafap.klassfile.models

data class WhileRef(
    val condLabel: LabelRef,
    val bodyLabel: LabelRef,
    val breakLabel: LabelRef,
)