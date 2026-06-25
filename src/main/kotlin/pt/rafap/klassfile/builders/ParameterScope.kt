package pt.rafap.klassfile.builders

import pt.rafap.klassfile.models.KlassDesc
import pt.rafap.klassfile.models.ParamRef
import pt.rafap.klassfile.utils.klassDescOf

class ParameterScope {
    private var params: MutableList<ParamRef<*>> = mutableListOf()

    fun param(name: String, type: KlassDesc<*>): ParamRef<*> {
        return ParamRef(name, type, 0).also { params.add(it) }
    }

    inline fun <reified T : Any> param(name: String = "") = param(name, klassDescOf<T>())

    fun build(): List<ParamRef<*>> = params.toList()
}