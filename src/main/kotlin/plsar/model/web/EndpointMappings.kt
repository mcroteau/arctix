package plsar.model.web

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class EndpointMappings {
    var mappings: MutableMap<String?, EndpointMapping>
    fun add(key: String?, endpointMapping: EndpointMapping) {
        mappings[key] = endpointMapping
    }

    operator fun get(key: String?): EndpointMapping? {
        return if (mappings.containsKey(key)) {
            mappings[key]
        } else null
    }

    operator fun contains(key: String?): Boolean {
        return mappings.containsKey(key)
    }

    fun setMappings(m: ConcurrentMap<String?, EndpointMapping?>?) {
        mappings = mappings
    }

    init {
        mappings = ConcurrentHashMap()
    }
}