package eos.util

import com.sun.net.httpserver.HttpExchange

class UriTranslator(var support: Support, var httpExchange: HttpExchange) {
    fun translate(): String? {
        val uriPre = httpExchange.requestURI.toString()
        val parts = uriPre.split("\\?".toRegex()).toTypedArray()
        var uri: String? = parts[0]
        if (uri == "") {
            uri = "/"
        }
        if (uri!!.endsWith("/") &&
            uri != "/"
        ) {
            uri = support.removeLast(uri)
        }
        return uri
    }

    val parameters: String
        get() {
            val uriPre = httpExchange.requestURI.toString()
            val parts = uriPre.split("\\?".toRegex()).toTypedArray()
            return if (parts.size > 1) {
                parts[1]
            } else ""
        }
}