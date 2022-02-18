package plsar.util

import com.sun.net.httpserver.Headers
import plsar.PLSAR
import plsar.model.web.HttpRequest
import java.io.*
import java.lang.reflect.Type
import java.math.BigDecimal
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

class Support {
    var isJar: Boolean
    fun removeLast(s: String?): String {
        return if (s == null || s.length == 0) "" else s.substring(0, s.length - 1)
    }

    val isFat: Boolean
        get() {
            var uri: String? = null
            try {
                uri = classesUri
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return if (uri!!.contains("jar:file:")) true else false
        }

    fun getPayload(bytes: ByteArray?): String {
        val sb = StringBuilder()
        for (b in bytes!!) {
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    fun getPayloadBytes(requestStream: InputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        try {
            val buf = ByteArray(1024 * 19)
            var bytesRead = 0
            while (requestStream.read(buf).also { bytesRead = it } != -1) {
                bos.write(buf, 0, bytesRead)
            }
            requestStream.close()
            bos.close()
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return bos.toByteArray()
    }

    val jarEntries: Enumeration<JarEntry>
        get() {
            val jarFile = jarFile
            return jarFile!!.entries()
        }

    fun getName(nameWithExt: String?): String {
        val index = nameWithExt!!.lastIndexOf(".")
        var qualifiedName = nameWithExt
        if (index > 0) {
            qualifiedName = qualifiedName.substring(index + 1)
        }
        return qualifiedName.toLowerCase()
    }

    val main: String
        get() {
            try {
                val jarFile = jarFile
                val jarEntry = jarFile!!.getJarEntry("META-INF/MANIFEST.MF")
                val `in` = jarFile.getInputStream(jarEntry)
                val scanner = Scanner(`in`)
                var line = ""
                do {
                    line = scanner.nextLine()
                    if (line.contains("Main-Class")) {
                        line = line.replace("Main-Class", "")
                        break
                    }
                } while (scanner.hasNext())
                line = line.replace(":", "").trim { it <= ' ' }
                return line
            } catch (ioex: IOException) {
                ioex.printStackTrace()
            }
            throw IllegalStateException("Apologies, it seems you are trying to run this as a jar but have not main defined.")
        }
    val jarFile: JarFile?
        get() {
            try {
                val jarUri = PLSAR::class.java.classLoader.getResource("plsar/")
                val jarPath = jarUri.path.substring(5, jarUri.path.indexOf("!"))
                return JarFile(jarPath)
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
            return null
        }

    @get:Throws(Exception::class)
    val classesUri: String?
        get() {
            var classesUri = Paths.get("src", "main", "kotlin")
                .toAbsolutePath()
                .toString()
            val classesDir = File(classesUri)
            if (classesDir.exists()) {
                return classesUri
            }
            classesUri = this.javaClass.getResource("").toURI().toString()
            if (classesUri == null) {
                throw Exception("A8i : unable to locate class uri")
            }
            return classesUri
        }

    operator fun get(request: HttpRequest, cls: Class<*>): Any? {
        return propagate(request, cls)
    }

    fun propagate(request: HttpRequest, cls: Class<*>): Any? {
        var `object`: Any? = null
        try {
            `object` = cls.getConstructor().newInstance()
            val fields = cls.declaredFields
            for (field in fields) {
                val name = field.name
                val value = request.value(name)
                if (value != null &&
                    value != ""
                ) {
                    field.isAccessible = true
                    val type: Type = field.type
                    if (type.typeName == "int" || type.typeName == "java.lang.Integer") {
                        field[`object`] = Integer.valueOf(value)
                    }
                    if (type.typeName == "double" || type.typeName == "java.lang.Double") {
                        field[`object`] = java.lang.Double.valueOf(value)
                    }
                    if (type.typeName == "float" || type.typeName == "java.lang.Float") {
                        field[`object`] = java.lang.Float.valueOf(value)
                    }
                    if (type.typeName == "long" || type.typeName == "java.lang.Long") {
                        field[`object`] = java.lang.Long.valueOf(value)
                    }
                    if (type.typeName == "boolean" || type.typeName == "java.lang.Boolean") {
                        field[`object`] = java.lang.Boolean.valueOf(value)
                    }
                    if (type.typeName == "java.math.BigDecimal") {
                        field[`object`] = BigDecimal(value)
                    }
                    if (type.typeName == "java.lang.String") {
                        field[`object`] = value
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return `object`
    }

    val project: String
        get() = if (isJar) {
            val jarFile = jarFile
            val path = jarFile!!.name
            var bits = path.split("/".toRegex()).toTypedArray()
            if (bits.size == 0) {
                bits = path.split("\\".toRegex()).toTypedArray()
            }
            val namePre = bits[bits.size - 1]
            namePre.replace(".jar", "")
        } else {
            ""
        }

    fun convert(`in`: InputStream): StringBuilder {
        val builder = StringBuilder()
        val scanner = Scanner(`in`)
        do {
            builder.append(
                """
    ${scanner.nextLine()}
    
    """.trimIndent()
            )
        } while (scanner.hasNext())
        try {
            `in`.close()
        } catch (ioex: IOException) {
            ioex.printStackTrace()
        }
        return builder
    }

    fun getCookie(cookieName: String, headers: Headers?): String {
        var value = ""
        if (headers != null) {
            val cookies = headers["Cookie"]
            if (cookies != null) {
                for (cookie in cookies) {
                    val bits = cookie.split(";".toRegex()).toTypedArray()
                    for (completes in bits) {
                        val parts = completes.split("=".toRegex()).toTypedArray()
                        val key = parts[0].trim { it <= ' ' }
                        if (parts.size > 1) {
                            if (key == cookieName) {
                                value = parts[1].trim { it <= ' ' }
                            }
                        }
                    }
                }
            }
        }
        //returning the last one.
        return value
    }

    companion object {
        fun SESSION_GUID(z: Int): String {
            val CHARS = ".01234567890"
            val guid = StringBuilder()
            guid.append("A8i.")
            val rnd = Random()
            while (guid.length < z) {
                val index = (rnd.nextFloat() * CHARS.length).toInt()
                guid.append(CHARS[index])
            }
            return guid.toString()
        }

        @get:Throws(Exception::class)
        val resourceUri: String
            get() {
                val resourceUri = Paths.get("src", "main", "resources")
                    .toAbsolutePath()
                    .toString()
                val resourceDir = File(resourceUri)
                if (resourceDir.exists()) {
                    return resourceUri
                }
                val RESOURCES_URI = "/src/main/resources/"
                val indexUri = PLSAR::class.java.getResource(RESOURCES_URI)
                    ?: throw FileNotFoundException("A8i : unable to find resource $RESOURCES_URI")
                return indexUri.toURI().toString()
            }
    }

    init {
        isJar = isFat
    }
}