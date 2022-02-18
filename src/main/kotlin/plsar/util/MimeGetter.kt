package plsar.util

import java.util.*

class MimeGetter(var resourceUri: String?) {
    fun within(key: String): Boolean {
        return MIME_MAP.containsKey(key)
    }

    fun resolve(): String? {
        val key = getExt(resourceUri).toLowerCase()
        return if (MIME_MAP.containsKey(key)) MIME_MAP[key] else "text/html"
    }

    companion object {
        fun getExt(path: String?): String {
            val slashIndex = path!!.lastIndexOf('/')
            val basename = if (slashIndex < 0) path else path.substring(slashIndex + 1)
            val dotIdx = basename.lastIndexOf('.')
            return if (dotIdx >= 0) {
                basename.substring(dotIdx + 1)
            } else {
                ""
            }
        }

        val MIME_MAP: MutableMap<String, String> = HashMap()

        init {
            MIME_MAP["appcache"] = "text/cache-manifest"
            MIME_MAP["css"] = "text/css"
            MIME_MAP["gif"] = "image/gif"
            MIME_MAP["html"] = "text/html"
            MIME_MAP["js"] = "application/javascript"
            MIME_MAP["json"] = "application/json"
            MIME_MAP["jpg"] = "image/jpeg"
            MIME_MAP["jpeg"] = "image/jpeg"
            MIME_MAP["mp4"] = "video/mp4"
            MIME_MAP["mp3"] = "audio/mp3"
            MIME_MAP["pdf"] = "application/pdf"
            MIME_MAP["png"] = "image/png"
            MIME_MAP["svg"] = "image/svg+xml"
            MIME_MAP["xlsm"] = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            MIME_MAP["xml"] = "application/xml"
            MIME_MAP["zip"] = "application/zip"
            MIME_MAP["md"] = "text/plain"
            MIME_MAP["txt"] = "text/plain"
            MIME_MAP["php"] = "text/plain"
        }
    }
}