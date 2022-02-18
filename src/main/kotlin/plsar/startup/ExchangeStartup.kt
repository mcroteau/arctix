package plsar.startup

import plsar.Plsar
import plsar.processor.UxProcessor
import plsar.util.*
import plsar.web.Interceptor
import plsar.web.Pointcut
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.*

class ExchangeStartup(
    val port : Int?,
    var pointcuts: Map<String?, Pointcut?>,
    var interceptors: Map<String?, Interceptor?>,
    var uxProcessor: UxProcessor) {

    var cache: Plsar.Cache? = null

    @Throws(Exception::class)
    fun start() {

        var inputStream = this.javaClass.getResourceAsStream("/src/main/resources/plsar.props")
        if (inputStream == null) {
            try {
                val uri: String = Support.resourceUri + File.separator + "plsar.props"
                inputStream = FileInputStream(uri)
            } catch (fe: FileNotFoundException) {
            }
        }
        if (inputStream == null) {
            throw Exception("plsar.props not found in src/main/resources/")
        }
        val props = Properties()
        props.load(inputStream)
        val env = props["plsar.env"]
        var noAction = true
        var createDb = false
        var dropDb = false

        if (env != null) {
            val environment = env.toString().replace("\\s+".toRegex(), "")
            val properties = Arrays.asList(*environment.split(",".toRegex()).toTypedArray())
            for (prop in properties) {
                if (prop == "create") {
                    noAction = false
                    createDb = true
                }
                if (prop == "drop") {
                    noAction = false
                    dropDb = true
                }
                if (prop == "update" || prop == "plain" || prop == "basic" || prop == "stub" || prop == "") {
                    noAction = true
                }
            }
        }
        if (noAction && (createDb || dropDb)) throw Exception(
            "You need to either set plsar.env=basic for basic systems that do not need " +
                    "a database connection, or plsar.env=create to create a db using src/main/resource/create-db.sql, " +
                    "or plsar.env=create,drop to both create and drop a database."
        )
        val resourcesProp = props["plsar.assets"]
        val propertiesProp = props["plsar.properties"]
        var resourcesPre: List<String> = ArrayList()
        if (resourcesProp != null) {
            val resourceStr = resourcesProp.toString()
            if (resourceStr != "") {
                resourcesPre = Arrays.asList(*resourceStr.split(",".toRegex()).toTypedArray())
            }
        }
        var propertiesPre: List<String> = ArrayList()
        if (propertiesProp != null) {
            val propString = propertiesProp.toString()
            if (propString != "") {
                propertiesPre = Arrays.asList(*propString.split(",".toRegex()).toTypedArray())
            }
        }
        val resources: MutableList<String> = ArrayList()
        if (!resourcesPre.isEmpty()) {
            for (resource in resourcesPre) {
                resource.replace("\\s+".toRegex(), "")
                resources.add(resource)
            }
        }
        val propertiesFiles: MutableList<String> = ArrayList()
        if (!propertiesPre.isEmpty()) {
            for (property in propertiesPre) {
                property.replace("\\s+".toRegex(), "")
                if (property == "this") {
                    propertiesFiles.add("plsar.props")
                }else {
                    propertiesFiles.add(property)
                }
            }
        }
        val settings = Settings()
        settings.isCreateDb = createDb
        settings.isDropDb = dropDb
        settings.isNoAction = noAction
        settings.resources = resources
        settings.propertiesFiles = propertiesFiles
        val repo = Plsar.Repo()
        cache = Plsar.Cache.Builder()
            .withSettings(settings)
            .withPointCuts(pointcuts)
            .withInterceptors(interceptors)
            .withUxProcessor(uxProcessor)
            .withRepo(repo)
            .make()
        Startup.Builder()
            .withPort(port)
            .withRepo(repo)
            .withCache(cache)
            .withSettings(settings)
            .build()
    }
}