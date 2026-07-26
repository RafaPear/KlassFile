package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.InvalidSlotIndexError

class LocalsStorage(params: List<OrderedRef<*>> = emptyList()) {
    private val locals = params.sortedBy { it.order }.toMutableList()
    private var localIdx = locals.maxOfOrNull { it.order } ?: -1

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> addLocal(name: String, type: KlassDesc<T>): LocalRef<T> {
        locals.firstOrNull { it is LocalRef<*> && it.name == name}?.let {
            if (it.type != type) {
                throw IllegalArgumentException("Local variable '$name' already exists with a different type: ${it.type}. Cannot add with type: $type")
            }
            return it as LocalRef<T>
        }

        val local = LocalRef(name, type, ++localIdx)
        locals.add(local)
        return local
    }


    fun getLocal(slot: Int): OrderedRef<*> {
        return locals.firstOrNull { it.order == slot }
            ?: throw InvalidSlotIndexError(slot)
    }

    fun print() {
        println("Locals:")
        locals.forEach { local ->
            println("  $local (type: ${local.type}, slot: ${local.order})")
        }
    }
}
