package eos.web

import com.sun.net.httpserver.HttpExchange
import eos.model.web.HttpRequest

interface Interceptor {
    fun intercept(request: HttpRequest?, httpExchange: HttpExchange?)
}