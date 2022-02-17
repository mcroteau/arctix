package eos.processor

import eos.Eos
import eos.annotate.*
import eos.model.Element
import eos.model.InstanceDetails
import eos.model.web.MethodFeature
import eos.util.Support
import java.lang.reflect.Method
import java.util.*

class ConfigurationProcessor(var cache: Eos.Cache?) {

    var support: Support
    var methods: MutableMap<String, MethodFeature>
    var iterableMethods: MutableList<MethodFeature>
    var processedMethods: MutableSet<MethodFeature?>
    var issues: MutableMap<String?, Int?>

    @Throws(Exception::class)
    fun run(): ConfigurationProcessor {
        setMapDependencyMethods()
        setIterableMethods(methods)
        while (!allDependenciesProcessed()) {
            process(0)
        }
        return this
    }

    @Throws(Exception::class)
    protected fun process(idx: Int) {
        var idx = idx
        val classCount = cache?.objects?.size
        if (idx > iterableMethods.size) idx = 0

        for (z in idx until iterableMethods.size) {
            val methodFeature = iterableMethods[z]
            val method = methodFeature.method
            val methodName = support.getName(method!!.name)
            val instance = methodFeature.instance
            try {
                val dependency = method.invoke(instance)
                val clsName = support.getName(dependency.javaClass.name)
                if (cache?.objects?.get(clsName) != null) {
                    cache?.objects?.get(clsName)?.instance = dependency
                } else {
                    val instanceDetails = InstanceDetails()
                    instanceDetails.instanceClass = dependency.javaClass
                    instanceDetails.name = clsName
                    instanceDetails.instance = dependency
                    cache?.objects?.set(clsName, instanceDetails)
                }
                createAddElement(method, dependency)
                processedMethods.add(methodFeature)
            } catch (ex: Exception) {
                process(z + 1)
                if (issues[methodName] != null) {
                    var count = issues[methodName]!!
                    count++
                    issues.replace(methodName, count)
                } else {
                    issues[methodName] = 1
                }
                if (issues[methodName] != null &&
                    issues[methodName]!! >= classCount!!
                ) {
                    val builder = StringBuilder()
                    for ((key, value) in issues) {
                        builder.append(
                            """       $key :: $value attempts 
"""
                        )
                    }
                    throw Exception("The following dependencies have not been resolved : \n\n\n$builder\n\n$ex")
                }
            }
        }
    }

    private fun setIterableMethods(methods: Map<String, MethodFeature>): Boolean {
        for ((_, value) in methods) {
            iterableMethods.add(value)
        }
        return true
    }

    protected fun allDependenciesProcessed(): Boolean {
        return processedMethods.size == iterableMethods.size
    }

    protected fun createAddElement(method: Method?, `object`: Any?) {
        val element = Element()
        element.element = `object`
        val classKey = support.getName(method!!.name)
        cache?.elementStorage?.elements?.set(classKey, element)
    }

    @Throws(Exception::class)
    protected fun setMapDependencyMethods() {
        for (config in cache?.elementProcessor!!.configs) {
            var instance : Any? = null
            val constructors = config!!.constructors
            for (constructor in constructors) {
                if (constructor.parameterCount == 0) {
                    instance = constructor.newInstance()
                }
            }
            val declaredMethods = Arrays.asList(*config!!.declaredMethods)
            for (method in declaredMethods) {
                if (method.isAnnotationPresent(Dependency::class.java)) {
                    val methodKey = method.name.lowercase()
                    if (methods.containsKey(methodKey)) {
                        throw Exception("More than one dependency with the same name defined : " + method.name)
                    }
                    if (cache?.elementStorage?.elements?.containsKey(methodKey) == true) {
                        println("\n\n")
                        println("Warning: you elements being injected twice, once by configuration, the other via @Bind.")
                        println("Take a look at " + config!!.name + " and @Bind for " + method.name)
                        println("\n\n")
                        val existingElement = cache?.elementStorage?.elements!![methodKey]
                        existingElement?.element = instance
                        cache?.elementStorage?.elements?.replace(methodKey, existingElement)
                    }
                    val methodFeature = MethodFeature()
                    methodFeature.name = method.name
                    methodFeature.method = method
                    methodFeature.instance = instance
                    methods[methodKey] = methodFeature
                }
            }
            val declaredFields = Arrays.asList(*config!!.declaredFields)
            for (field in declaredFields) {
                if (field.isAnnotationPresent(Property::class.java)) {
                    val property = field.getAnnotation(Property::class.java)
                    val key: String = property.value
                    if (cache?.propertyStorage?.properties?.containsKey(key) != true) {
                        throw Exception("$key property is missing")
                    }
                    val value = cache?.propertyStorage?.properties!![key]
                    field.isAccessible = true
                    field[instance] = value
                }
            }
        }
    }

    init {
        support = Support()
        methods = HashMap()
        processedMethods = HashSet<MethodFeature?>()
        iterableMethods = ArrayList()
        issues = HashMap()
    }
}