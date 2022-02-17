package eos.processor

import eos.Eos
import eos.annotate.*
import eos.model.InstanceDetails
import eos.util.Support
import java.lang.reflect.Field
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.*

class AnnotationProcessor(var cache: Eos.Cache?) {
    var support: Support
    var processed: MutableMap<String?, InstanceDetails?>
    var annotations: MutableList<InstanceDetails?>

    @Throws(Exception::class)
    fun run() {
        while (!allAnnotationsProcessed()) {
            processAnnotations(0)
            break
        }
    }

    @Throws(Exception::class)
    private fun processAnnotations(idx: Int) {
        var idx = idx
        if (idx > annotations.size) idx = 0
        for (z in idx until annotations.size) {
            val instanceDetails = annotations[z]
            val fieldsCount = getAnnotatedFieldsCount(instanceDetails?.instanceClass)
            var processedFieldsCount = 0
            val `object` = instanceDetails?.instance
            val fields = instanceDetails?.instanceClass?.declaredFields
            for (field in fields!!) {
                if (field.isAnnotationPresent(Bind::class.java)) {
                    val fieldKey = field.name.lowercase()
                    if (cache?.elementStorage?.elements?.containsKey(fieldKey) == true) {
                        val element = cache?.elementStorage?.elements?.get(fieldKey)?.element
                        field.isAccessible = true
                        field[`object`] = element
                        processedFieldsCount++
                    } else {
                        processAnnotations(z + 1)
                    }
                }
                if (field.isAnnotationPresent(Property::class.java)) {
                    val annotation = field.getAnnotation(Property::class.java)
                    val key: String = annotation.value
                    if (cache?.propertyStorage?.properties?.containsKey(key) == true) {
                        field.isAccessible = true
                        val value = cache?.propertyStorage?.properties!![key]
                        attachValue(field, `object`, value)
                        processedFieldsCount++
                    } else {
                        processAnnotations(z + 1)
                        throw Exception(field.name + " is missing on " + `object`!!.javaClass.name)
                    }
                }
            }
            if (fieldsCount !==
                processedFieldsCount
            ) {
                processAnnotations(z + 1)
            } else {
                val key = support.getName(instanceDetails.name)
                processed[key] = instanceDetails
            }
        }
    }

    @Throws(Exception::class)
    protected fun attachValue(field: Field, `object`: Any?, stringValue: String?) {
        val type: Type = field.type
        if (type.typeName == "java.lang.String") {
            field[`object`] = stringValue
        }
        if (type.typeName == "boolean" || type.typeName == "java.lang.Boolean") {
            val value = java.lang.Boolean.valueOf(stringValue)
            field[`object`] = value
        }
        if (type.typeName == "int" || type.typeName == "java.lang.Integer") {
            val value = Integer.valueOf(stringValue)
            field[`object`] = value
        }
        if (type.typeName == "float" || type.typeName == "java.lang.Float") {
            val value = java.lang.Float.valueOf(stringValue)
            field[`object`] = value
        }
        if (type.typeName == "double" || type.typeName == "java.lang.Double") {
            val value = java.lang.Double.valueOf(stringValue)
            field[`object`] = value
        }
        if (type.typeName == "java.math.BigDecimal") {
            val value = BigDecimal(stringValue)
            field[`object`] = value
        }
    }

    @Throws(Exception::class)
    protected fun getAnnotatedFieldsCount(clazz: Class<*>?): Int {
        var count = 0
        val fields = clazz!!.declaredFields
        for (field in fields) {
            if (field.isAnnotationPresent(Bind::class.java)) {
                count++
            }
            if (field.isAnnotationPresent(Property::class.java)) {
                count++
            }
        }
        return count
    }

    private fun map() {
        cache?.elementProcessor?.annotatedInstances?.forEach { (_, instanceDetails) ->
            if (!annotations.contains(instanceDetails)) annotations?.add(instanceDetails)
        }
    }

    protected fun allAnnotationsProcessed(): Boolean {
        return processed.size == cache?.elementProcessor?.annotatedInstances?.size
    }

    init {
        support = Support()
        processed = HashMap()
        annotations = ArrayList()
        map()
    }
}