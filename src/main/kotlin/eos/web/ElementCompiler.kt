package eos.web

import com.sun.net.httpserver.HttpExchange
import eos.Eos
import eos.model.web.FormElement
import eos.model.web.HttpRequest
import eos.model.web.HttpSession
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Thank you Mr. Walter
 * https://gist.github.com/JensWalter/0f19780d131d903879a2
 */
class ElementCompiler(
    var cache: Eos.Cache?,
    var bytes: ByteArray?,
    var sessions: MutableMap<String?, HttpSession?>,
    var httpExchange: HttpExchange
) {
    fun compile(): HttpRequest {
        val headers = httpExchange.requestHeaders
        val httpRequest = HttpRequest(sessions, httpExchange)
        val contentType = headers.getFirst("Content-Type")
        var delimeter = ""
        if (contentType != null) {
            val bits = contentType.split("boundary=".toRegex()).toTypedArray()
            if (bits.size > 1) {
                delimeter = bits[1]
                val sb = StringBuilder()
                for (b in bytes!!) {
                    sb.append(b.toChar())
                }
                val payload = sb.toString()
                val data = getElements(delimeter, payload)
                for (formElement in data) {
                    val key = formElement.name
                    httpRequest[key] = formElement
                }
            } else if (bytes!!.size > 0) {
                var query = ""
                try {
                    query = String(bytes!!, Charset.defaultCharset())
                    query = URLDecoder.decode(query, StandardCharsets.UTF_8.name())
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                for (entry in query.split("&".toRegex()).toTypedArray()) {
                    val element = FormElement()
                    val keyValue = entry.split("=".toRegex(), 2).toTypedArray()
                    val key = keyValue[0]
                    if (keyValue.size > 1) {
                        val value = keyValue[1]
                        element.name = key
                        element.setValue(value)
                    } else {
                        element.name = key
                        element.setValue("")
                    }
                    httpRequest.data()[key] = element
                }
            }
        }
        return httpRequest
    }

    protected fun getElements(delimeter: String?, payload: String): List<FormElement> {
        val formData: MutableList<FormElement> = ArrayList()
        var index = payload.indexOf("name=\"")
        val data = getData(index, delimeter, payload)
        formData.add(data)
        while (index >= 0) {
            index = payload.indexOf("name=\"", index + 1)
            if (index >= 0) {
                val dataDos = getData(index, delimeter, payload)
                formData.add(dataDos)
            }
        }
        return formData
    }

    protected fun getData(index: Int, delimeter: String?, payload: String): FormElement {
        val formElement = FormElement()
        val startName = payload.indexOf("\"", index + 1)
        val endName = payload.indexOf("\"", startName + 1)
        val name = payload.substring(startName + 1, endName)
        formElement.name = name
        val fileIdx = payload.indexOf("filename=", endName + 1)

        //if we are equal to 3, then we are on the same line,
        //we have a file and we can proceed.
        if (fileIdx - endName == 3) {
            val startFile = payload.indexOf("\"", fileIdx + 1)
            val endFile = payload.indexOf("\"", startFile + 1)
            val fileName = payload.substring(startFile + 1, endFile)
            formElement.fileName = fileName
            val startContent = payload.indexOf("Content-Type", endFile + 1)
            val startType = payload.indexOf(":", startContent + 1)
            val endType = payload.indexOf("\r\n", startType + 1)
            val type = payload.substring(startType + 1, endType).trim { it <= ' ' }
            formElement.contentType = type
            val startBytes = payload.indexOf("\r\n", endType + 1)
            val endBytes = payload.indexOf(delimeter!!, startBytes + 4)
            val value = payload.substring(startBytes, endBytes)
            val startValue = startBytes + 2
            var endValue = endBytes
            if (value.endsWith("--")) {
                endValue = endBytes - 2 // -- tells us we are at the end
            }
            endValue = endValue - 2 //finicky, need to remove the final 2 bytes
            val baos = ByteArrayOutputStream()
            for (z in startValue until endValue) {
                val b = bytes!![z]
                baos.write(b.toInt())
            }
            formElement.fileBytes = baos.toByteArray()
        } else {
            //a plain old value here.
            val startValue = payload.indexOf("\r\n", endName + 1)
            val endValue = payload.indexOf(delimeter!!, startValue + 1)
            var value = payload.substring(startValue + 1, endValue)
            if (value.endsWith("\r\n--")) {
                val lastbit = value.indexOf("\r\n--")
                value = value.substring(0, lastbit).trim { it <= ' ' }
            }
            formElement.setValue(value)
        }
        return formElement
    }
}