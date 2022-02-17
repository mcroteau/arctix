package eos.model.web

import java.util.*

class HttpResponse {
    var title: String? = null
    var keywords: String? = null
    var description: String? = null
    var data: MutableMap<String?, Any?>
    operator fun set(key: String?, value: Any?) {
        data[key] = value
    }

    operator fun get(key: String?): Any? {
        return if (data.containsKey(key)) {
            data[key]
        } else null
    }

    fun setTitle(title: String?) {
        this.title = title
    }

    fun getTitle(): String? {
        return if (title != null) {
            title
        } else ""
    }

    fun getKeywords(): String? {
        return if (keywords != null) {
            keywords
        } else ""
    }

    fun setKeywords(keywords: String?) {
        this.keywords = keywords
    }

    fun getDescription(): String? {
        return if (description != null) {
            description
        } else ""
    }

    fun setDescription(description: String?) {
        this.description = description
    }

    fun data(): Map<String?, Any?> {
        return data
    }

    init {
        data = HashMap()
    }
}