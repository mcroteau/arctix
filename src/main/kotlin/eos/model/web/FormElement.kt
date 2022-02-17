package eos.model.web

class FormElement {
    var name: String? = null
    var value: String? = null
    var isFile = false
    var fileName: String? = null
    var contentType: String? = null
    var fileBytes: ByteArray? = null
}