package pt.rafap.klassfile.models

import pt.rafap.klassfile.utils.InvalidSlotIndexError

class LocalsStorage(params: List<OrderedRef<*>> = emptyList()) {
    private val locals = params.sortedBy { it.order }.toMutableList()
    private var localIdx = locals.maxOfOrNull { it.order } ?: -1

    fun <T : Any> addLocal(name: String, type: KlassDesc<T>): LocalRef<T> {
        val local = LocalRef(name, type, ++localIdx)
        locals.add(local)
        return local
    }


    fun getLocal(slot: Int): OrderedRef<*> {
        return locals.firstOrNull { it.order == slot }
            ?: throw InvalidSlotIndexError(slot)
    }
}
