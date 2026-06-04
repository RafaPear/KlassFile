package pt.rafap.klassfile.models

import java.lang.classfile.CodeBuilder
import java.lang.classfile.constantpool.FieldRefEntry
import java.lang.constant.ClassDesc

data class FieldRef<T>(val owner: ClassDesc, val name: String, val type: ClassDesc) {
	companion object {
		fun <T> CodeBuilder.toFieldRefEntry(ref: FieldRef<T>): FieldRefEntry =
			constantPool().fieldRefEntry(ref.owner, ref.name, ref.type)
	}
}
