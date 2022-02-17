package eos.web

import com.sun.net.httpserver.HttpExchange
import eos.model.web.HttpRequest

interface Pointcut {
    val key: String?
    val isEvaluation: Boolean
    fun isTrue(httpRequest: HttpRequest?, exchange: HttpExchange?): Boolean
    fun process(httpRequest: HttpRequest?, exchange: HttpExchange?): String?

    companion object {
        const val KEY = "key:attribute"
        const val EVALUATION = false
    }
}