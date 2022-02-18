package plsar.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import plsar.PLSAR
import plsar.annotate.*
import plsar.model.web.*
import plsar.util.MimeGetter
import plsar.util.ResourceResponse
import plsar.util.Support
import plsar.util.UriTranslator
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class HttpTransmission(var cache: PLSAR.Cache?) : HttpHandler {
    var support: Support
    var sessions: MutableMap<String?, HttpSession?>
    override fun handle(httpExchange: HttpExchange) {
        val outputStream = httpExchange.responseBody
        try {
            val inputStream = httpExchange.requestBody
            val payloadBytes = support.getPayloadBytes(inputStream)
            val requestCompiler = ElementCompiler(cache, payloadBytes, sessions, httpExchange)
            val httpRequest = requestCompiler.compile()
            val payload = support.getPayload(payloadBytes)
            httpRequest.requestBody = payload
            val interceptors = cache?.interceptors
            for ((_, interceptor) in interceptors!!) {
                interceptor!!.intercept(httpRequest, httpExchange)
            }
            val transformer = UriTranslator(support, httpExchange)
            val requestUri = transformer.translate()
            httpRequest.setValues(transformer.parameters)
            val httpVerb = httpExchange.requestMethod.lowercase()
            if (ResourceResponse.isResource(requestUri, cache)) {
                ResourceResponse.Builder()
                    .withCache(cache)
                    .withRequestUri(requestUri)
                    .withHttpVerb(httpVerb)
                    .withHttpExchange(httpExchange)
                    .make()
                    .serve()
                return
            }
            val httpResponse = getHttpResponse(httpExchange)
            if (httpRequest.session != null) {
                val httpSession = httpRequest.session
                for ((key, value1) in httpSession!!.data()) {
                    val value = value1.toString()
                    httpResponse[key] = value
                }
            }
            val endpointMapping = getHttpMapping(httpVerb, requestUri)
            if (endpointMapping == null) {
                try {
                    val message = "404 not found."
                    httpExchange.sendResponseHeaders(200, message.length.toLong())
                    outputStream.write(message.toByteArray())
                    outputStream.flush()
                    outputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return
            }
            val signature = getEndpointParameters(requestUri, httpRequest, httpResponse, endpointMapping, httpExchange)
            val method = endpointMapping.method
            method!!.isAccessible = true
            var design: String? = null
            if (method.isAnnotationPresent(Design::class.java)) {
                val annotationDos = method.getAnnotation(Design::class.java)
                design = annotationDos.value
            }

            //todo:spread operator
            val instance: Any? = endpointMapping.classDetails?.instance
            val numberOfParameters = method.parameters

            var methodResponse : String ?
            if(numberOfParameters.size == 0) {
                methodResponse = method.invoke(instance) as String
            }else{
                methodResponse = method.invoke(instance, *signature) as String
            }

            if (method.isAnnotationPresent(Text::class.java) ||
                method.isAnnotationPresent(Plain::class.java)
            ) {
                val headers = httpExchange.responseHeaders
                headers.add("content-type", "text/html")
                httpExchange.sendResponseHeaders(200, methodResponse.length.toLong())
                outputStream.write(methodResponse.toByteArray())
            } else if (method.isAnnotationPresent(JsonOutput::class.java)) {
                val headers = httpExchange.responseHeaders
                headers.add("content-type", "application/json")
                httpExchange.sendResponseHeaders(200, methodResponse.length.toLong())
                outputStream.write(methodResponse.toByteArray())
            } else if (method.isAnnotationPresent(Media::class.java)) {
                val mimeGetter = MimeGetter(requestUri)
                val headers = httpExchange.responseHeaders
                headers.add("content-type", mimeGetter.resolve())
                outputStream.write(methodResponse.toByteArray())
            } else if (methodResponse.startsWith(REDIRECT)) {
                httpExchange.setAttribute("message", httpResponse["message"])
                val redirect = getRedirect(methodResponse)
                val headers = httpExchange.responseHeaders
                headers.add("Location", redirect)
                httpExchange.sendResponseHeaders(302, -1)
                httpExchange.close()
                return
            } else {
                val title = httpResponse?.title
                val keywords = httpResponse?.keywords
                val description = httpResponse?.description
                if (!support.isJar) {
                    val webPath = Paths.get("webapp")
                    if (methodResponse.startsWith("/")) {
                        methodResponse = methodResponse.replaceFirst("/".toRegex(), "")
                    }
                    val htmlPath = webPath.toFile().absolutePath + File.separator + methodResponse
                    val viewFile = File(htmlPath)
                    if (!viewFile.exists()) {
                        try {
                            val message = "view $htmlPath cannot be found."
                            httpExchange.sendResponseHeaders(200, message.length.toLong())
                            outputStream.write(message.toByteArray())
                            outputStream.flush()
                            outputStream.close()
                            return
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    val fis: InputStream = FileInputStream(viewFile)
                    val unebaos = ByteArrayOutputStream()
                    val bytes = ByteArray(1024 * 13)
                    var unelength: Int
                    while (fis.read(bytes).also { unelength = it } != -1) {
                        unebaos.write(bytes, 0, unelength)
                    }
                    val pageContent = unebaos.toString(StandardCharsets.UTF_8.name())
                    if (design != null) {
                        val designPath = webPath.toFile().absolutePath + File.separator + design
                        val designFile = File(designPath)
                        if (!designFile.exists()) {
                            try {
                                val message = "design $designPath cannot be found."
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                                return
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        val dis: InputStream = FileInputStream(designFile)
                        val baos = ByteArrayOutputStream()
                        var deuxlength: Int
                        while (dis.read(bytes).also { deuxlength = it } != -1) {
                            baos.write(bytes, 0, deuxlength)
                        }
                        val designContent = baos.toString(StandardCharsets.UTF_8.name())
                        if (!designContent.contains("<eos:content/>")) {
                            try {
                                val message = "your template file is missing <eos:content/>"
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        val bits = designContent.split("<eos:content/>".toRegex()).toTypedArray()
                        var header = bits[0]
                        val bottom = bits[1]
                        header = header + pageContent
                        var completePage = header + bottom
                        completePage = completePage.replace("${title}", title!!)
                        if (keywords != null) {
                            completePage = completePage.replace("${keywords}", keywords)
                        }
                        if (description != null) {
                            completePage = completePage.replace("${description}", description)
                        }
                        var designOutput = ""
                        try {
                            val uxProcessor = cache?.uxProcessor
                            val pointcuts = cache?.pointCuts
                            designOutput =
                                uxProcessor!!.process(pointcuts, completePage, httpResponse, httpRequest, httpExchange)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            try {
                                val message = "Please check your html template file. " + ex.message
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        val bs = designOutput.toByteArray(charset("utf-8"))
                        httpExchange.sendResponseHeaders(200, bs.size.toLong())
                        outputStream.write(bs)
                    } else {
                        var pageOutput : String ?
                        try {
                            val uxProcessor = cache?.uxProcessor
                            val pointcuts = cache?.pointCuts
                            pageOutput =
                                uxProcessor!!.process(pointcuts, pageContent, httpResponse, httpRequest, httpExchange)
                            if (!pageOutput.startsWith("<html>")) {
                                pageOutput = "<html>$pageOutput"
                                pageOutput = "$pageOutput</html>"
                            }
                            httpExchange.sendResponseHeaders(200, pageOutput.length.toLong())
                            outputStream.write(pageOutput.toByteArray())
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            try {
                                val message = "Please check your html template file. " + ex.message
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                } else {

                    if (methodResponse.startsWith("/")) methodResponse = methodResponse.replaceFirst("/".toRegex(), "")
                    val pagePath = "/webapp/$methodResponse"
                    val pageInput = this.javaClass.getResourceAsStream(pagePath)
                    val unebaos = ByteArrayOutputStream()
                    val bytes = ByteArray(1024 * 13)
                    var unelength: Int
                    while (pageInput.read(bytes).also { unelength = it } != -1) {
                        unebaos.write(bytes, 0, unelength)
                    }
                    val pageContent = unebaos.toString(StandardCharsets.UTF_8.name())
                    if (design != null) {
                        val designPath = "/webapp/$design"
                        val designInput = this.javaClass.getResourceAsStream(designPath)
                        val baos = ByteArrayOutputStream()
                        var length: Int
                        while (designInput.read(bytes).also { length = it } != -1) {
                            baos.write(bytes, 0, length)
                        }
                        val designContent = baos.toString(StandardCharsets.UTF_8.name())
                        if (!designContent.contains("<eos:content/>")) {
                            try {
                                val message = "your template file is missing <eos:content/>"
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        val bits = designContent.split("<eos:content/>".toRegex()).toTypedArray()
                        var header = bits[0]
                        val bottom = bits[1]
                        header = header + pageContent
                        var completePage = header + bottom
                        completePage = completePage.replace("\${title}", title!!)
                        if (keywords != null) {
                            completePage = completePage.replace("\${keywords}", keywords)
                        }
                        if (description != null) {
                            completePage = completePage.replace("\${description}", description)
                        }
                        var designOutput = ""
                        try {
                            val uxProcessor = cache?.uxProcessor
                            val pointcuts = cache?.pointCuts
                            designOutput =
                                uxProcessor!!.process(pointcuts, completePage, httpResponse, httpRequest, httpExchange)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            try {
                                val message = "Please check your html template file. " + ex.message
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                        val bs = designOutput.toByteArray(charset("utf-8"))
                        httpExchange.sendResponseHeaders(200, bs.size.toLong())
                        outputStream.write(bs)
                    } else {
                        var pageOutput : String ?
                        try {
                            val uxProcessor = cache?.uxProcessor
                            val pointcuts = cache?.pointCuts
                            pageOutput =
                                uxProcessor!!.process(pointcuts, pageContent, httpResponse, httpRequest, httpExchange)
                            if (!pageOutput.startsWith("<html>")) {
                                pageOutput = "<html>$pageOutput"
                                pageOutput = "$pageOutput</html>"
                            }
                            httpExchange.sendResponseHeaders(200, pageOutput.length.toLong())
                            outputStream.write(pageOutput.toByteArray())
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            try {
                                val message = "Please check your html template file. " + ex.message
                                httpExchange.sendResponseHeaders(200, message.length.toLong())
                                outputStream.write(message.toByteArray())
                                outputStream.flush()
                                outputStream.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
        } catch (ccex: ClassCastException) {
            ccex.printStackTrace()
        } catch (ex: Exception) {
            ex.printStackTrace()
            try {
                val message = "not good. let us know."
                httpExchange.sendResponseHeaders(200, message.length.toLong())
                outputStream.write(message.toByteArray())
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    protected fun getRedirect(uri: String): String {
        val redirectBits = uri.split("]".toRegex()).toTypedArray()
        return if (redirectBits.size > 1) {
            redirectBits[1]
        } else ""
    }

    private fun getEndpointParameters(requestUri: String?,
                                        httpRequest: HttpRequest?,
                                        httpResponse: HttpResponse,
                                        endpointMapping: EndpointMapping,
                                        httpExchange: HttpExchange
                                    ): Array<Any> {
        val endpointValues = getEndpointValues(requestUri, endpointMapping)
        val params: MutableList<Any> = ArrayList()
        val typeNames = endpointMapping.typeNames
        var idx = 0
        for (z in typeNames!!.indices) {
            val type = typeNames[z]
            if (type == "com.sun.net.httpserver.HttpExchange") {
                params.add(httpExchange)
            }
            if (type == "plsar.model.web.HttpRequest") {
                params.add(httpRequest!!)
            }
            if (type == "plsar.model.web.HttpResponse") {
                params.add(httpResponse)
            }
            if (type == "int" || type == "java.lang.Integer") {
                params.add(Integer.valueOf(endpointValues[idx].value))
                idx++
            }
            if (type == "long" || type == "java.lang.Long") {
                params.add(java.lang.Long.valueOf(endpointValues[idx].value))
                idx++
            }
            if (type == "java.lang.String") {
                params.add(endpointValues[idx].value)
                idx++
            }
        }
        return params.toTypedArray()
    }

    protected fun getEndpointValues(uri: String?, mapping: EndpointMapping): List<EndpointPosition> {
        val pathParts = getPathParts(uri)
        val regexParts = getRegexParts(mapping)
        val httpValues: MutableList<EndpointPosition> = ArrayList()
        for (n in regexParts!!.indices) {
            var regex = regexParts[n]
            if (regex?.contains("A-Za-z0-9") == true) {
                httpValues.add(EndpointPosition(n, pathParts[n]))
            }
        }
        return httpValues
    }

    protected fun getPathParts(uri: String?): List<String> {
        return Arrays.asList(*uri!!.split("/".toRegex()).toTypedArray())
    }

    protected fun getRegexParts(mapping: EndpointMapping): List<String>? {
        return mapping?.regexedPath?.split("/".toRegex())
    }

    protected fun getHttpMapping(verb: String, uri: String?): EndpointMapping? {
        cache?.endpointMappings?.mappings?.forEach { (_, mapping) ->
            var mappingUri = mapping.path
            if (!mapping.path?.startsWith("/")!!) {
                mappingUri = "/$mappingUri"
            }
            if (mappingUri == uri) {
                return mapping
            }
        }


        for ((_, mapping) in cache?.endpointMappings?.mappings!!) {
            val matcher = Pattern.compile(mapping.regexedPath!!)
                .matcher(uri.toString())
            var mappingUri = mapping.path
            if (!mapping.path?.startsWith("/")!!) {
                mappingUri = "/$mappingUri"
            }
            if (matcher.matches() && mapping.verb?.lowercase() == verb &&
                variablesMatchUp(uri, mapping) &&
                lengthMatches(uri, mappingUri)
            ) {
                return mapping
            }
        }
        return null
    }

    protected fun lengthMatches(uri: String?, mappingUri: String?): Boolean {
        val uriBits = uri!!.split("/".toRegex()).toTypedArray()
        val mappingBits = mappingUri!!.split("/".toRegex()).toTypedArray()
        return uriBits.size == mappingBits.size
    }

    protected fun variablesMatchUp(uri: String?, endpointMapping: EndpointMapping): Boolean {
        val bits = Arrays.asList(*uri!!.split("/".toRegex()).toTypedArray())
        val urlBitFeatures = endpointMapping.urlBitFeatures
        val urlBits = urlBitFeatures?.urlBits
        val typeParameters = endpointMapping.method?.parameterTypes
        val parameterTypes = getParameterTypes(typeParameters)
        var idx = 0
        for (q in urlBits!!.indices) {
            val urlBit = urlBits[q]
            if (urlBit.isVariable == true) {
                try {
                    val methodType = parameterTypes[idx]
                    val bit = bits[q]
                    if (bit != "") {
                        if (methodType == "java.lang.Integer") {
                            bit.toInt()
                        }
                        if (methodType == "java.lang.Long") {
                            bit.toLong()
                        }
                    }
                    idx++
                } catch (ex: Exception) {
                    return false
                }
            }
        }
        return true
    }

    fun getParameterTypes(clsParamaters: Array<Class<*>>?): List<String> {
        val parameterTypes: MutableList<String> = ArrayList()
        for (cls in clsParamaters!!) {
            val type = cls.typeName
            if (!type.contains("HttpExchange") &&
                !type.contains("HttpRequest") &&
                !type.contains("HttpResponse")
            ) {
                parameterTypes.add(type)
            }
        }
        return parameterTypes
    }

    protected fun getHttpResponse(exchange: HttpExchange): HttpResponse {
        val httpResponse = HttpResponse()
        if (exchange.getAttribute("message") != null) {
            httpResponse["message"] = exchange.getAttribute("message")
            exchange.setAttribute("message", "")
        }
        return httpResponse
    }

    companion object {
        const val REDIRECT = "[redirect]"
    }

    init {
        support = Support()
        sessions = ConcurrentHashMap()
    }
}