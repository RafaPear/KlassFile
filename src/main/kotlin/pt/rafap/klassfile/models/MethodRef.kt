package pt.rafap.klassfile.models

import java.lang.constant.ClassDesc
import java.lang.constant.MethodTypeDesc
import java.lang.reflect.Modifier

data class MethodRef(
	val owner: ClassDesc,
	val name: String,
	val methodTypeDesc: MethodTypeDesc,
	val flags: Int,
	val isConstructor: Boolean,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is MethodRef) return false

		if (owner != other.owner) return false
		if (name != other.name) return false
		if (methodTypeDesc.returnType().toString() != other.methodTypeDesc.returnType().toString()) return false
		if (methodTypeDesc.parameterList().map { it.toString() } != other.methodTypeDesc.parameterList()
				.map { it.toString() }) return false
		if (isConstructor != other.isConstructor) return false

		return true
	}

	private fun toModifiers(): List<String> = buildList {
		when {
			Modifier.isPublic(flags) -> add("public")
			Modifier.isProtected(flags) -> add("protected")
			Modifier.isPrivate(flags) -> add("private")
		}
		if (Modifier.isAbstract(flags)) add("abstract")
		if (Modifier.isStatic(flags)) add("static")
		if (Modifier.isFinal(flags)) add("final")
		if (Modifier.isNative(flags)) add("native")
	}

	override fun toString(): String = buildString {

		// Modifiers
		val modifiers = toModifiers()
		if (modifiers.isNotEmpty())
			modifiers.joinTo(this, " ", postfix = " ")

		// Owner
		append(owner.displayName())

		// Method Name
		if (!isConstructor) append(".$name")

		// Parameters
		methodTypeDesc
			.parameterList()
			.joinTo(this, ", ", prefix = "(", postfix = ")") { it.displayName() }

		append(": ")
		append(methodTypeDesc.returnType().displayName())
	}

	override fun hashCode(): Int {
		var result = 1
		result = 31 * result + isConstructor.hashCode()
		result = 31 * result + owner.hashCode()
		result = 31 * result + name.hashCode()
		result = 31 * result + methodTypeDesc.parameterList().map { it.toString() }.hashCode()
		result = 31 * result + methodTypeDesc.returnType().toString().hashCode()
		return result
	}
}
