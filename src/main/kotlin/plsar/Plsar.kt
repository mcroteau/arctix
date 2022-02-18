package plsar

import com.sun.net.httpserver.HttpServer
import plsar.cargo.ElementStorage
import plsar.cargo.ObjectStorage
import plsar.cargo.PropertyStorage
import plsar.model.Element
import plsar.model.InstanceDetails
import plsar.model.web.EndpointMappings
import plsar.processor.ElementProcessor
import plsar.processor.EndpointProcessor
import plsar.processor.UxProcessor
import plsar.startup.ExchangeStartup
import plsar.util.Settings
import plsar.util.Support
import plsar.web.HttpTransmission
import plsar.web.Interceptor
import plsar.web.Pointcut
import java.io.IOException
import java.lang.reflect.Type
import java.net.InetSocketAddress
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.sql.DataSource

class Plsar(builder: Builder) {

    val port : Int?
    var support: Support?
    var httpServer: HttpServer?
    var pointcuts: MutableMap<String?, Pointcut?>
    var interceptors: MutableMap<String?, Interceptor?>

    @Throws(Exception::class)
    fun run(): Plsar {
        val uxProcessor = UxProcessor()
        val exchangeStartup = ExchangeStartup(port, pointcuts, interceptors, uxProcessor)
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

        fun port(port: Int?): Builder {
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

        fun create(): Plsar {
            return Plsar(this)
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


    class Repo {

        var ds: DataSource? = null

        fun setDataSource(ds: DataSource?) {
            this.ds = ds
        }

        operator fun get(preSql: String, params: Array<Any?>, cls: Class<*>): Any? {
            var result: Any? = null
            var sql = ""
            try {
                sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                val rs = stmt.executeQuery(sql)
                if (rs.next()) {
                    result = extractData(rs, cls)
                }
                if (result == null) {
                    throw Exception("$cls not found using '$sql'")
                }
                connection.commit()
                connection.close()
            } catch (ex: SQLException) {
                println("bad sql grammar : $sql")
                println("\n\n\n")
                ex.printStackTrace()
            } catch (ex: Exception) {
            }
            return result
        }

        fun getInteger(preSql: String, params: Array<Any?>): Int? {
            var result: Int? = null
            var sql = ""
            try {
                sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                val rs = stmt.executeQuery(sql)
                if (rs.next()) {
                    result = rs.getObject(1).toString().toInt()
                }
                if (result == null) {
                    throw Exception("no results using '$sql'")
                }
                connection.commit()
                connection.close()
            } catch (ex: SQLException) {
                println("bad sql grammar : $sql")
                println("\n\n\n")
                ex.printStackTrace()
            } catch (ex: Exception) {
            }
            return result
        }

        fun getLong(preSql: String, params: Array<Any?>): Long? {
            var result: Long? = null
            var sql = ""
            try {
                sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                val rs = stmt.executeQuery(sql)
                if (rs.next()) {
                    result = rs.getObject(1).toString().toLong()
                }
                if (result == null) {
                    throw Exception("no results using '$sql'")
                }
                connection.commit()
                connection.close()
            } catch (ex: SQLException) {
                println("bad sql grammar : $sql")
                println("\n\n\n")
                ex.printStackTrace()
            } catch (ex: Exception) {
            }
            return result
        }

        fun save(preSql: String, params: Array<Any?>): Boolean {
            try {
                val sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                stmt.execute(sql)
                connection.commit()
                connection.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
                return false
            }
            return true
        }

        fun getList(preSql: String, params: Array<Any?>, cls: Class<*>): List<Any?> {
            var results: MutableList<Any?> = ArrayList()
            try {
                val sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                val rs = stmt.executeQuery(sql)
                results = ArrayList()
                while (rs.next()) {
                    val obj = extractData(rs, cls)
                    results.add(obj)
                }
                connection.commit()
                connection.close()
            } catch (ccex: ClassCastException) {
                println("")
                println("Wrong Class type, attempted to cast the return data as a $cls")
                println("")
                ccex.printStackTrace()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return results
        }

        fun update(preSql: String, params: Array<Any?>): Boolean {
            try {
                val sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                val rs = stmt.execute(sql)
                connection.commit()
                connection.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
                return false
            }
            return true
        }

        fun delete(preSql: String, params: Array<Any?>): Boolean {
            try {
                val sql = hydrateSql(preSql, params)
                val connection = ds!!.connection
                val stmt = connection.createStatement()
                stmt.execute(sql)
                connection.commit()
                connection.close()
            } catch (ex: Exception) {
                return false
            }
            return true
        }

        protected fun hydrateSql(sql: String, params: Array<Any?>): String {
            var sql = sql
            for (`object` in params) {
                if (`object` != null) {
                    var parameter = `object`.toString()
                    if (`object`.javaClass.typeName == "java.lang.String") {
                        parameter = parameter.replace("'", "''")
                            .replace("$", "\\$")
                            .replace("#", "\\#")
                            .replace("@", "\\@")
                    }
                    sql = sql.replaceFirst("\\[\\+\\]".toRegex(), parameter)
                } else {
                    sql = sql.replaceFirst("\\[\\+\\]".toRegex(), "null")
                }
            }
            return sql
        }

        @Throws(Exception::class)
        protected fun extractData(rs: ResultSet, cls: Class<*>): Any {
            var `object` = Any()
            val constructors = cls.constructors
            for (constructor in constructors) {
                if (constructor.parameterCount == 0) {
                    `object` = constructor.newInstance()
                }
            }
            val fields = `object`.javaClass.declaredFields
            for (field in fields) {
                field.isAccessible = true
                val originalName = field.name
                val regex = "([a-z])([A-Z]+)"
                val replacement = "$1_$2"
                val name = originalName.replace(regex.toRegex(), replacement).toLowerCase()
                val type: Type = field.type
                if (hasColumn(rs, name)) {
                    if (type.typeName == "int" || type.typeName == "java.lang.Integer") {
                        field[`object`] = rs.getInt(name)
                    } else if (type.typeName == "double" || type.typeName == "java.lang.Double") {
                        field[`object`] = rs.getDouble(name)
                    } else if (type.typeName == "float" || type.typeName == "java.lang.Float") {
                        field[`object`] = rs.getFloat(name)
                    } else if (type.typeName == "long" || type.typeName == "java.lang.Long") {
                        field[`object`] = rs.getLong(name)
                    } else if (type.typeName == "boolean" || type.typeName == "java.lang.Boolean") {
                        field[`object`] = rs.getBoolean(name)
                    } else if (type.typeName == "java.math.BigDecimal") {
                        field[`object`] = rs.getBigDecimal(name)
                    } else if (type.typeName == "java.lang.String") {
                        field[`object`] = rs.getString(name)
                    }
                }
            }
            return `object`
        }

        companion object {
            @Throws(SQLException::class)
            fun hasColumn(rs: ResultSet, columnName: String): Boolean {
                val rsmd = rs.metaData
                for (x in 1..rsmd.columnCount) {
                    if (columnName == rsmd.getColumnName(x).toLowerCase()) {
                        return true
                    }
                }
                return false
            }
        }
    }



    companion object {
        const val SECURITYTAG = "plsar.sessions"
        const val RESOURCES = "/src/main/resources/"
    }

    init {
        port  = builder.port
        support = builder.support
        httpServer = builder.httpServer
        pointcuts = HashMap()
        interceptors = HashMap()
    }
}