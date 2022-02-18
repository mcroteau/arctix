package plsar.processor

import plsar.Plsar
import plsar.annotate.*
import plsar.model.Element
import plsar.model.InstanceDetails
import java.util.*

class ElementProcessor(var cache: Plsar.Cache?) {
    var jdbcCount = 0
    var serviceCount = 0
    var elementCount = 0

    var configurations: MutableList<Class<*>?>
    var httpInstances: MutableMap<String?, InstanceDetails?>
    var annotatedInstances: MutableMap<String?, InstanceDetails?>

    fun run(): ElementProcessor {
        for ((_, value) in cache!!.objects) {
            val cls = value.instanceClass
            if (cls!!.isAnnotationPresent(Config::class.java)) {
                configurations?.add(cls)
            }
        }
        for (entry in cache?.objects!!.entries) {
            val cls = entry.value.instanceClass
            if (cls!!.isAnnotationPresent(plsar.annotate.Element::class.java)) {
                buildAddElement(entry)
                elementCount++
            }
            if (cls!!.isAnnotationPresent(Data::class.java)) {
                buildAddElement(entry)
                jdbcCount++
            }
            if (cls!!.isAnnotationPresent(Service::class.java)) {
                buildAddElement(entry)
                serviceCount++
            }
            if (cls!!.isAnnotationPresent(HttpHandler::class.java)) {
                httpInstances[entry.key] = entry.value
            }
            val fields = cls!!.declaredFields
            for (field in fields) {
                if (field.isAnnotationPresent(Inject::class.java)) {
                    annotatedInstances[entry.key] = entry.value
                }
                if (field.isAnnotationPresent(Property::class.java)) {
                    annotatedInstances[entry.key] = entry.value
                }
            }
        }
        return this
    }

    protected fun buildAddElement(entry: Map.Entry<String?, InstanceDetails?>) {
        val element = Element()
        val key = entry.key
        val instance = entry.value?.instance
        element.element = instance
        cache?.elementStorage!!.elements[key] = element
    }

    init {
        configurations = ArrayList()
        httpInstances = HashMap()
        annotatedInstances = HashMap()
    }
}