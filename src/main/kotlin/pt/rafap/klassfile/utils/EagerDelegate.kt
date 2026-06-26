package pt.rafap.klassfile.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A read-only property delegate that computes its value lazily and caches it.
 *
 * The delegate also materializes the value during `provideDelegate`, allowing DSL
 * builders to capture the property name as soon as the delegate is bound.
 */
class EagerDelegate<out R : Any>(
	private val buildValue: (thisRef: Any?, property: KProperty<*>) -> R
) : ReadOnlyProperty<Any?, R> {

	private var ref: R? = null

	/** Returns the cached value or computes it on first access. */
	override fun getValue(thisRef: Any?, property: KProperty<*>): R {
		val copy = ref
		if (copy != null) return copy

		val value = buildValue(thisRef, property)
		ref = value
		return value
	}

	/** Forces eager initialization when the delegate is attached to a property. */
	operator fun provideDelegate(
		thisRef: Any?,
		property: KProperty<*>,
	): EagerDelegate<R> {
		getValue(thisRef, property) // Force method creation to capture the property name
		return this
	}
}