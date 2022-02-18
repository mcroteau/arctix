package plsar.util

import com.sun.net.httpserver.HttpExchange
import plsar.Plsar
import java.io.*
import java.nio.file.Paths

class ResourceResponse(builder: Builder) {
    val GET = "get"
    val WEBAPP = "webapp"
    val CONTENTTYPE = "Content-Type"
    var cache: Plsar.Cache?
    var requestUri: String?
    var httpVerb: String?
    var httpExchange: HttpExchange?
    var support: Support

    @Throws(IOException::class)
    fun serve() {
        val fis: InputStream
        if (support.isJar) {
            if (requestUri!!.startsWith("/")) requestUri = requestUri!!.replaceFirst("/".toRegex(), "")
            val resourcePath = "/webapp/$requestUri"
            val ris = this.javaClass.getResourceAsStream(resourcePath)
            if (ris != null) {
                val unebaos = ByteArrayOutputStream()
                val bytes = ByteArray(1024 * 13)
                var unelength: Int
                while (ris.read(bytes).also { unelength = it } != -1) {
                    unebaos.write(bytes, 0, unelength)
                }
                val mimeGetter = MimeGetter(requestUri)
                val mimeType = mimeGetter.resolve()
                httpExchange!!.responseHeaders[CONTENTTYPE] = mimeType
                if (httpVerb == GET) {
                    httpExchange!!.sendResponseHeaders(200, unebaos.size().toLong())
                    val os = httpExchange!!.responseBody
                    os.write(unebaos.toByteArray())
                    os.close()
                    os.flush()
                } else {
                    httpExchange!!.sendResponseHeaders(200, -1)
                }
                ris.close()
            }
        } else {
            val webPath = Paths.get(WEBAPP).toString()
            val filePath = webPath + requestUri
            val file = File(filePath)
            fis = try {
                FileInputStream(filePath)
            } catch (e: FileNotFoundException) {
                //Thank you: https://stackoverflow.com/users/35070/phihag
                outputAlert(httpExchange, 404)
                return
            }
            if (fis != null) {
                val mimeGetter = MimeGetter(filePath)
                val mimeType = mimeGetter.resolve()
                httpExchange!!.responseHeaders[CONTENTTYPE] = mimeType
                if (httpVerb == GET) {
                    httpExchange!!.sendResponseHeaders(200, file.length())
                    val os = httpExchange!!.responseBody
                    copyStream(fis, os)
                    os.close()
                    os.flush()
                } else {
                    httpExchange!!.sendResponseHeaders(200, -1)
                }
                fis.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun outputAlert(httpExchange: HttpExchange?, errorCode: Int) {
        val message = "A8i/ resource missing! $errorCode"
        val messageBytes = message.toByteArray(charset("utf-8"))
        httpExchange!!.responseHeaders["Content-Type"] = "text/plain; charset=utf-8"
        httpExchange.sendResponseHeaders(errorCode, messageBytes.size.toLong())
        val os = httpExchange.responseBody
        os.write(messageBytes)
        os.close()
    }

    @Throws(IOException::class)
    private fun copyStream(`is`: InputStream, os: OutputStream) {
        val bytes = ByteArray(1024 * 13)
        var n: Int
        while (`is`.read(bytes).also { n = it } >= 0) {
            os.write(bytes, 0, n)
        }
    }

    class Builder {
        var cache: Plsar.Cache? = null
        var requestUri: String? = null
        var httpVerb: String? = null
        var resources: List<String>? = null
        var httpExchange: HttpExchange? = null
        fun withRequestUri(requestUri: String?): Builder {
            this.requestUri = requestUri
            return this
        }

        fun withHttpVerb(httpVerb: String?): Builder {
            this.httpVerb = httpVerb
            return this
        }

        fun withCache(cache: Plsar.Cache?): Builder {
            this.cache = cache
            return this
        }

        fun withResources(resources: List<String>?): Builder {
            this.resources = resources
            return this
        }

        fun withHttpExchange(httpExchange: HttpExchange?): Builder {
            this.httpExchange = httpExchange
            return this
        }

        fun make(): ResourceResponse {
            return ResourceResponse(this)
        }
    }

    companion object {
        fun isResource(requestUri: String?, cache: Plsar.Cache?): Boolean {
            if (cache?.resources == null) return false
            val bits = requestUri!!.split("/".toRegex()).toTypedArray()
            if (bits.size > 1) {
                val resource = bits[1]
                if (cache?.resources!!.contains(resource)) return true
            }
            return false
        }
    }

    init {
        cache = builder.cache
        requestUri = builder.requestUri
        httpVerb = builder.httpVerb
        httpExchange = builder.httpExchange
        support = Support()
    }
}