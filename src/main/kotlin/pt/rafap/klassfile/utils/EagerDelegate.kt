package pt.rafap.klassfile.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class EagerDelegate<out R : Any>(
	private val buildValue: (thisRef: Any?, property: KProperty<*>) -> R
) : ReadOnlyProperty<Any?, R> {

	private var ref: R? = null

	override fun getValue(thisRef: Any?, property: KProperty<*>): R {
		val copy = ref
		if (copy != null) return copy

		val value = buildValue(thisRef, property)
		ref = value
		return value
	}

	operator fun provideDelegate(
		thisRef: Any?,
		property: KProperty<*>,
	): EagerDelegate<R> {
		getValue(thisRef, property) // Force method creation to capture the property name
		return this
	}
}