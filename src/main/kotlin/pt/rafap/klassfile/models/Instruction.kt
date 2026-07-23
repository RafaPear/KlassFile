package pt.rafap.klassfile.models

import java.lang.classfile.CodeBuilder

sealed class Instruction {
    class RawInstruction(val code: CodeBuilder.() -> Unit) : Instruction()
    class LabelInstruction(val label: LabelRef, ) : Instruction()
}