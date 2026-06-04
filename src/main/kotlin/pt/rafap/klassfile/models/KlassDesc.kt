package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.classDesc
import java.lang.constant.ClassDesc
import kotlin.reflect.KClass

data class KlassDesc<T: Any>(
	val classDesc: ClassDesc,
	val kClass: KClass<T>?,
) {
	companion object {
		inline fun <reified T: Any> withType(): KlassDesc<T> =
			KlassDesc(classDesc<T>(), T::class)

	}
}
