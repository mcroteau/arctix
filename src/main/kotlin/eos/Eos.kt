package eos

import com.sun.net.httpserver.HttpServer
import eos.cargo.ElementStorage
import eos.cargo.ObjectStorage
import eos.cargo.PropertyStorage
import eos.jdbc.Repo
import eos.model.Element
import eos.model.InstanceDetails
import eos.model.web.EndpointMappings
import eos.processor.ElementProcessor
import eos.processor.EndpointProcessor
import eos.processor.UxProcessor
import eos.startup.ExchangeStartup
import eos.util.Settings
import eos.util.Support
import eos.web.HttpTransmission
import eos.web.Interceptor
import eos.web.Pointcut
import java.io.IOException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class Eos(builder: Builder) {

    var support: Support?
    var httpServer: HttpServer?
    var pointcuts: MutableMap<String?, Pointcut?>
    var interceptors: MutableMap<String?, Interceptor?>

    @Throws(Exception::class)
    fun run(): Eos {
        val uxProcessor = UxProcessor()
        val exchangeStartup = ExchangeStartup(pointcuts, interceptors, uxProcessor)
        exchangeStartup.start()
        val cache = exchangeStartup.cache
        val modulator = HttpTransmission(cache)
        httpServer!!.createContext("/", modulator)
        httpServer!!.start()
        return this
    }

    fun registerPointcut(pointcut: Pointcut): Boolean {
        val key = support!!.getName(pointcut.javaClass.name)
        pointcuts[key] = pointcut
        return true
    }

    fun registerInterceptor(interceptor: Interceptor): Boolean {
        val key = support!!.getName(interceptor.javaClass.name)
        interceptors[key] = interceptor
        return true
    }

    class Builder {
        var port: Int? = null
        var httpServer: HttpServer? = null
        var executors: ExecutorService? = null
        var support: Support? = null
        fun withPort(port: Int?): Builder {
            this.port = port
            return this
        }

        @Throws(IOException::class)
        fun spawn(numberThreads: Int): Builder {
            support = Support()
            executors = Executors.newFixedThreadPool(numberThreads)
            httpServer = HttpServer.create(InetSocketAddress(port!!), 0)
            httpServer?.setExecutor(executors)
            return this
        }

        fun create(): Eos {
            return Eos(this)
        }
    }

    class Cache(builder: Builder) {
        var events: Any? = null
        var settings: Settings?
        var pointCuts: Map<String?, Pointcut?>?
        var interceptors: Map<String?, Interceptor?>?
        var objectStorage: ObjectStorage
        var propertyStorage: PropertyStorage
        var elementStorage: ElementStorage
        var repo: Repo?
        var uxProcessor: UxProcessor?
        var endpointProcessor: EndpointProcessor? = null
        var elementProcessor: ElementProcessor? = null
        var endpointMappings: EndpointMappings? = null
        fun getElement(name: String): Any? {
            val key = name.lowercase()
            return if (elementStorage.elements.containsKey(key)) {
                elementStorage.elements[key]?.element
            } else null
        }

        val elements: Map<String?, Element?>?
            get() = elementStorage.elements
        var resources: List<String?>? = null
            get() = settings?.resources
        var propertiesFiles: List<String?>? = null
            get() = settings?.propertiesFiles

        val objects: MutableMap<String, InstanceDetails>
            get() = objectStorage.objects

        class Builder {
            var repo: Repo? = null
            var settings: Settings? = null
            var uxProcessor: UxProcessor? = null
            var pointcuts: Map<String?, Pointcut?>? = null
            var interceptors: Map<String?, Interceptor?>? = null
            fun withSettings(settings: Settings?): Builder {
                this.settings = settings
                return this
            }

            fun withPointCuts(pointcuts: Map<String?, Pointcut?>?): Builder {
                this.pointcuts = pointcuts
                return this
            }

            fun withInterceptors(interceptors: Map<String?, Interceptor?>?): Builder {
                this.interceptors = interceptors
                return this
            }

            fun withUxProcessor(uxProcessor: UxProcessor?): Builder {
                this.uxProcessor = uxProcessor
                return this
            }

            fun withRepo(repo: Repo?): Builder {
                this.repo = repo
                return this
            }

            fun make(): Cache {
                return Cache(this)
            }
        }

        init {
            repo = builder.repo
            pointCuts = builder.pointcuts
            interceptors = builder.interceptors
            settings = builder.settings
            uxProcessor = builder.uxProcessor
            elementStorage = ElementStorage()
            propertyStorage = PropertyStorage()
            objectStorage = ObjectStorage()
        }
    }

    companion object {
        const val SECURITYTAG = "eos.sessions"
        const val RESOURCES = "/src/main/resources/"
    }

    init {
        support = builder.support
        httpServer = builder.httpServer
        pointcuts = HashMap()
        interceptors = HashMap()
    }
}