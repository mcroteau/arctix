package eos.startup

import eos.Eos
import eos.jdbc.Mediator
import eos.jdbc.Repo
import eos.model.Element
import eos.processor.*
import eos.util.Settings
import eos.util.Support
import java.lang.reflect.InvocationTargetException
import java.util.*
import javax.sql.DataSource

class Startup {
    class Builder {
        var cache: Eos.Cache? = null
        var repo: Repo? = null
        var support: Support
        var settings: Settings? = null

        fun withRepo(repo: Repo?): Builder {
            this.repo = repo
            return this
        }

        fun withCache(cache: Eos.Cache?): Builder {
            this.cache = cache
            return this
        }

        fun withSettings(settings: Settings?): Builder {
            this.settings = settings
            return this
        }

        private fun setAttributes() {
            val element = Element()
            element.element = cache
            cache?.elementStorage?.elements?.set("cache", element)
            val repoElement = Element()
            repoElement.element = repo
            cache?.elementStorage?.elements?.set("repo", repoElement)
            val supportElement = Element()
            supportElement.element = support
            cache?.elementStorage?.elements?.set("support", supportElement)
            if (cache?.resources == null) cache!!.resources = ArrayList()
            if (cache?.propertiesFiles == null) cache!!.propertiesFiles = ArrayList()
        }

        @Throws(Exception::class)
        private fun initDatabase() {
            val mediator = Mediator(settings, support, cache)
            val element = Element()
            element.element = mediator
            cache?.elementStorage?.elements?.set("dbmediator", element)
            mediator.createDb()
        }

        @Throws(Exception::class)
        private fun validateDatasource() {
            val element = cache?.elementStorage?.elements?.get("datasource")
            if (element != null) {
                val dataSource = element.element as DataSource
                repo!!.setDataSource(dataSource)
            }
        }

        @Throws(Exception::class)
        private fun setDbAttributes() {
            validateDatasource()
            initDatabase()
        }

        @Throws(NoSuchMethodException::class, IllegalAccessException::class, InvocationTargetException::class)
        private fun dispatchEvent() {
            if (cache?.events != null) {
                val setupComplete =
                    cache?.events!!.javaClass.getDeclaredMethod("setupComplete", Eos.Cache::class.java)
                if (setupComplete != null) {
                    setupComplete.isAccessible = true
                    setupComplete.invoke(cache?.events, cache)
                }
            }
        }

        @Throws(Exception::class)
        private fun runElementsProcessor() {
            val elementsProcessor = ElementProcessor(cache).run()
            cache?.elementProcessor = elementsProcessor
        }

        @Throws(Exception::class)
        private fun runConfigProcessor() {
            if (cache?.elementProcessor?.configs != null &&
                cache?.elementProcessor?.configs?.size!! > 0
            ) {
                ConfigurationProcessor(cache).run()
            }
        }

        @Throws(Exception::class)
        private fun runAnnotationProcessor() {
            AnnotationProcessor(cache).run()
        }

        @Throws(Exception::class)
        private fun runEndpointProcessor() {
            val endpointProcessor = EndpointProcessor(cache).run()
            val endpointMappings = endpointProcessor.mappings
            cache?.endpointMappings = endpointMappings
        }

        @Throws(Exception::class)
        private fun runPropertiesProcessor() {
            if (!cache?.propertiesFiles?.isEmpty()!!) {
                PropertiesProcessor(cache).run()
            }
        }

        @Throws(Exception::class)
        private fun runInstanceProcessor() {
            InstanceProcessor(cache).run()
        }

        @Throws(Exception::class)
        private fun runProcessors() {
            runPropertiesProcessor()
            runInstanceProcessor()
            runElementsProcessor()
            runConfigProcessor()
            runAnnotationProcessor()
            runEndpointProcessor()
        }

        private fun sayReady() {
            val name = support.project
            println("[READY!] $name! : o . o . o . o . o . o . o . o . o . o . o . o  ")
        }

        @Throws(Exception::class)
        fun build(): Startup {
            setAttributes()
            runProcessors()
            setDbAttributes()
            sayReady()
            dispatchEvent()
            return Startup()
        }

        init {
            support = Support()
        }
    }
}