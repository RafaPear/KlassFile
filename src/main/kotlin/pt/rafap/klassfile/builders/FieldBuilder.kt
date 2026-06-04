package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.FieldRef
import pt.rafap.klassfile.utils.FieldBuilderDsl
import pt.rafap.klassfile.utils.classDesc
import java.lang.classfile.ClassBuilder
import java.lang.classfile.ClassFile.*
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.CD_void

@FieldBuilderDsl
@Suppress("unused")
class FieldBuilder(private val thisClassDesc: ClassDesc) {
	var fields = listOf<ClassBuilder.() -> Unit>()
		private set

	/**
	 * Defines a field with the given name, type, and access flags, then queues it to be emitted in the generated
	 * class.
	 *
	 * The returned [pt.rafap.klassfile.models.FieldRef] can be reused when generating bytecode that reads from or writes to the field.
	 *
	 * @param name the name of the field.
	 * @param type the field type as a [ClassDesc]. Defaults to [CD_void] if not specified.
	 * @param flags the field access flags. Defaults to [ACC_PRIVATE] if not specified.
	 * @return a [pt.rafap.klassfile.models.FieldRef] describing the generated field.
	 */
	fun <T> field(name: String, type: ClassDesc = CD_void, flags: Int = ACC_PRIVATE): FieldRef<T> {
		val ref = FieldRef<T>(thisClassDesc, name, type)
		fields += { withField(ref.name, ref.type, flags) }
		return ref
	}

	/**
	 * Defines a field with the given name and inferred type, then queues it to be emitted in the generated class.
	 *
	 * This overload infers the field type from [T] and delegates to [field].
	 *
	 * @param T the field type as a reified type parameter.
	 * @param name the name of the field.
	 * @param flags the field access flags. Defaults to [ACC_PRIVATE] if not specified.
	 */
	inline fun <reified T> field(name: String, flags: Int = ACC_PRIVATE) = field<T>(name, classDesc<T>(), flags)

	/**
	 * Defines a public field with the given name and inferred type.
	 *
	 * This is a convenience wrapper around [field] with [ACC_PUBLIC] applied.
	 *
	 * @param T the field type as a reified type parameter.
	 * @param name the name of the field.
	 * @return a [FieldRef] describing the generated public field.
	 */
	inline fun <reified T> publicField(name: String) = field<T>(name, classDesc<T>(), ACC_PUBLIC)

	/**
	 * Defines a static field with the given name and inferred type.
	 *
	 * This is a convenience wrapper around [field] with [ACC_STATIC] applied.
	 *
	 * @param T the field type as a reified type parameter.
	 * @param name the name of the field.
	 * @return a [FieldRef] describing the generated static field.
	 */
	inline fun <reified T> staticField(name: String) = field<T>(name, classDesc<T>(), ACC_STATIC)

}