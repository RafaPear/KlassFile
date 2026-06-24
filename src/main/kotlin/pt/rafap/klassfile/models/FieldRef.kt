package pt.rafap.klassfile.models

import java.lang.classfile.ClassBuilder
import java.lang.classfile.ClassFile.ACC_STATIC
import java.lang.classfile.CodeBuilder
import java.lang.classfile.constantpool.FieldRefEntry
import java.lang.constant.ClassDesc

data class FieldRef<T>(
	val owner: ClassDesc,
	val name: String,
	val type: ClassDesc,
	val flags: Int
) {
	val isStatic: Boolean = flags and ACC_STATIC != 0

	companion object {
		fun <T> CodeBuilder.toFieldRefEntry(ref: FieldRef<T>): FieldRefEntry =
			constantPool().fieldRefEntry(ref.owner, ref.name, ref.type)

		fun ClassBuilder.field(ref: FieldRef<*>): ClassBuilder = apply {
			withField(ref.name, ref.type, ref.flags)
		}
	}
}
