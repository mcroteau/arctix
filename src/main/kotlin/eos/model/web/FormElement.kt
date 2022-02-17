package eos.model.web

class FormElement {
    var name: String? = null
    var value: String? = null
    var isFile = false
    var fileName: String? = null
    var contentType: String? = null
    var fileBytes: ByteArray
    fun value(): String? {
        return value
    }

    fun setValue(value: String?) {
        this.value = value
    }

    fun getFileBytes(): ByteArray? {
        return fileBytes
    }

    fun setFileBytes(fileBytes: ByteArray) {
        this.fileBytes = fileBytes
    }
}