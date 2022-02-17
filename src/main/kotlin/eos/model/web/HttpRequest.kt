package eos.model.web

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import eos.Eos
import eos.util.Support
import java.util.*

class HttpRequest(var sessions: MutableMap<String?, HttpSession?>, httpExchange: HttpExchange) {
    var session: HttpSession? = null
    var httpExchange: HttpExchange
    var elements: MutableMap<String?, FormElement?>
    var requestBody: String? = null
    var support: Support
    fun data(): MutableMap<String?, FormElement?> {
        return elements
    }

    val headers: Headers
        get() = httpExchange.requestHeaders

    fun setSession() {
        val id = support.getCookie(Eos.Companion.SECURITYTAG, httpExchange.requestHeaders)
        if (sessions.containsKey(id)) {
            session = sessions[id]
        }
    }

    fun getSession(newitup: Boolean): HttpSession? {
        val id = support.getCookie(Eos.Companion.SECURITYTAG, httpExchange.requestHeaders)
        if (!newitup) {
            if (sessions.containsKey(id)) {
                session = sessions[id]
                return sessions[id]
            }
        } else if (newitup) {
            return getHttpSession()
        }
        return null //not good. how?
    }

    private fun getHttpSession(): HttpSession {
        var httpSession = HttpSession(sessions, httpExchange)
        sessions[httpSession.getId()] = httpSession
        val compound: String = Eos.Companion.SECURITYTAG + "=" + httpSession.getId()
        httpExchange.responseHeaders["Set-Cookie"] = compound
        httpSession = httpSession
        return httpSession
    }

    operator fun set(key: String?, formElement: FormElement?) {
        elements[key] = formElement
    }

    /**
     * getValue(String key) is a lookup
     * for a given form field and returns the
     * value for the given FormElement
     *
     * @see FormElement
     *
     *
     * @param key
     * @return returns the value for the given form field
     */
    fun value(key: String?): String? {
        return if (elements.containsKey(key)) {
            elements[key]!!.value()
        } else null
    }

    operator fun get(key: String?): FormElement? {
        return if (elements.containsKey(key)) {
            elements[key]
        } else null
    }

    fun getMultiple(key: String): Array<Any> {
        val values: MutableList<String?> = ArrayList()
        for ((key1, value) in elements) {
            if (key == key1 &&
                value!!.value() != null
            ) {
                values.add(value.value())
            }
        }
        return values.toTypedArray()!!
    }

    fun getPayload(key: String?): ByteArray? {
        if (elements.containsKey(key)) {
            if (elements[key]!!.getFileBytes() != null) {
                return elements[key]!!.getFileBytes()
            }
        }
        return null
    }

    fun setValues(parameters: String?) {
        val keyValues = parameters!!.split("&".toRegex()).toTypedArray()
        for (keyValue in keyValues) {
            val parts = keyValue.split("=".toRegex()).toTypedArray()
            if (parts.size > 1) {
                val key = parts[0]
                val value = parts[1]
                val formElement = FormElement()
                formElement.setName(key)
                formElement.setValue(value)
                elements[key] = formElement
            }
        }
    }

    init {
        support = Support()
        elements = HashMap()
        this.httpExchange = httpExchange
        setSession()
    }
}