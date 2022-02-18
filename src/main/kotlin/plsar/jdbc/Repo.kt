package plsar.jdbc

import java.lang.reflect.Type
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import javax.sql.DataSource

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