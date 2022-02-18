package plsar.web

import com.sun.net.httpserver.HttpExchange
import plsar.model.web.HttpRequest

interface Interceptor {
    fun intercept(request: HttpRequest?, httpExchange: HttpExchange?)
}