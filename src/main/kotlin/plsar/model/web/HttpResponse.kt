package plsar.model.web

import java.util.*

class HttpResponse {
    var title: String? = ""
    var keywords: String? = ""
    var description: String? = ""
    var data: MutableMap<String?, Any?>
    operator fun set(key: String?, value: Any?) {
        data[key] = value
    }

    operator fun get(key: String?): Any? {
        return if (data.containsKey(key)) {
            data[key]
        } else null
    }

    fun data(): Map<String?, Any?> {
        return data
    }

    init {
        data = HashMap()
    }
}