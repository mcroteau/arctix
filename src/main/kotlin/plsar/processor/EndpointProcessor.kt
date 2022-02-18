package plsar.processor

import plsar.PLSAR
import plsar.annotate.*
import plsar.annotate.verbs.Delete
import plsar.annotate.verbs.Get
import plsar.annotate.verbs.Post
import plsar.model.InstanceDetails
import plsar.model.web.*
import java.lang.reflect.Method
import java.util.*

class EndpointProcessor(var cache: PLSAR.Cache?) {
    var processed: MutableMap<String?, InstanceDetails?>
    var mappings: EndpointMappings

    @Throws(Exception::class)
    fun run(): EndpointProcessor {
        while (!allAnnotationsProcessed()) {
            processWebAnnotations()
        }
        return this
    }

    private fun allAnnotationsProcessed(): Boolean {
        return processed.size == cache?.elementProcessor?.httpInstances?.size
    }

    @Throws(Exception::class)
    private fun processWebAnnotations() {
        cache?.elementProcessor?.httpInstances?.forEach { (key, value) ->
            val clazz = value?.instanceClass
            val methods = clazz!!.declaredMethods
            for (method in methods) {
                if (method.isAnnotationPresent(Get::class.java)) {
                    setGetMapping(method, value)
                    processed[key] = value
                }
                if (method.isAnnotationPresent(Post::class.java)) {
                    setPostMapping(method, value)
                    processed[key] = value
                }
                if (method.isAnnotationPresent(Delete::class.java)) {
                    setDeleteMapping(method, value)
                    processed[key] = value
                }
            }
        }
    }

    @Throws(Exception::class)
    protected fun setGetMapping(method: Method, instanceDetails: InstanceDetails?) {
        val get = method.getAnnotation(Get::class.java)
        val path: String = get.value
        val mapping = EndpointMapping()
        mapping.verb = GET
        setBaseDetailsAdd(path, mapping, method, instanceDetails)
    }

    @Throws(Exception::class)
    protected fun setPostMapping(method: Method, instanceDetails: InstanceDetails?) {
        val post = method.getAnnotation(Post::class.java)
        val path: String = post.value
        val mapping = EndpointMapping()
        mapping.verb = POST
        setBaseDetailsAdd(path, mapping, method, instanceDetails)
    }

    @Throws(Exception::class)
    protected fun setDeleteMapping(method: Method, instanceDetails: InstanceDetails?) {
        val delete = method.getAnnotation(Delete::class.java)
        val path: String = delete.value
        val mapping = EndpointMapping()
        mapping.verb = DELETE
        setBaseDetailsAdd(path, mapping, method, instanceDetails)
    }

    @Throws(Exception::class)
    protected fun setBaseDetailsAdd(path: String,
                                    mapping: EndpointMapping,
                                    method: Method,
                                    instanceDetails: InstanceDetails?) {
        mapping.typeNames = ArrayList()
        val types = method.genericParameterTypes

        for (type in types) {
            mapping.typeNames!!.add(type.typeName)
        }

        val typeDetails: MutableList<TypeFeature> = ArrayList()
        val paramAnnotations = method.parameterAnnotations
        val paramTypes = method.parameterTypes

        for (n in paramAnnotations.indices) {
            for (a in paramAnnotations[n]) {
                if (a is Variable) {
                    val details = TypeFeature()
                    details.name = paramTypes[n].typeName
                    details.type = paramTypes[n].typeName
                    typeDetails.add(details)
                }
            }
        }

//https://regex101.com/r/sYeDyN/1
//\/(post){1}\/[A-Za-z0-9]*\/(paul){1}$
//\/(get){1}\/[A-Za-z0-9]\/[A-Za-z0-9]\/[A-Za-z0-9]\/$
        val regexPath = StringBuilder()
        regexPath.append("\\/(")
        var count = 0
        val parts = path.split("/".toRegex()).toTypedArray()
        for (part in parts) {
            count++
            if (part != "") {
                if (part.matches("(\\{[a-zA-Z]*\\})".toRegex())) {
                    regexPath.append("(.*[A-Za-z0-9])")
                    mapping.variablePositions.add(count - 1)
                } else {
                    regexPath.append("(" + part.lowercase() + "){1}")
                }
                if (count < parts.size) {
                    regexPath.append("\\/")
                }
            }
        }
        regexPath.append(")$")
        mapping.regexedPath = regexPath.toString()
        mapping.typeDetails = typeDetails
        mapping.path = path
        mapping.method = method
        mapping.classDetails = instanceDetails
        val key = mapping.verb + "-" + path
        if (mappings.contains(key)) {
            throw Exception("Request path + $path exists multiple times.")
        }
        val bits = path.split("/".toRegex()).toTypedArray()
        val urlBitFeatures = UrlBitFeatures()
        val urlBits: MutableList<UrlBit> = ArrayList()
        for (bit in bits) {
            val urlBit = UrlBit()
            if (bit.contains("{{") && bit.contains("}}")) {
                urlBit.isVariable = true
            } else {
                urlBit.isVariable = false
            }
            urlBits.add(urlBit)
        }
        urlBitFeatures.urlBits = urlBits
        mapping.urlBitFeatures = urlBitFeatures
        mappings.add(key, mapping)
    }

    companion object {
        const val GET = "Get"
        const val POST = "Post"
        const val PUT = "Put"
        const val DELETE = "Delete"
    }

    init {
        processed = HashMap()
        mappings = EndpointMappings()
    }
}