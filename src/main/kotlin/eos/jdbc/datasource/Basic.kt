package eos.jdbc.datasource

import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

class Basic(builder: Builder) : DataSource {
    var init = true
    var dbDriver: String?
    var dbUrl: String?
    var dbName: String?
    var dbUsername: String?
    var dbPassword: String?
    var loginTimeout: Int? = null
    var connection: Connection? = null

    class Builder {
        var dbUrl: String? = null
        var dbName: String? = null
        var dbUsername: String? = null
        var dbPassword: String? = null
        var dbDriver: String? = null
        fun url(dbUrl: String?): Builder {
            this.dbUrl = dbUrl
            return this
        }

        fun dbName(dbName: String?): Builder {
            this.dbName = dbName
            return this
        }

        fun username(dbUsername: String?): Builder {
            this.dbUsername = dbUsername
            return this
        }

        fun password(dbPassword: String?): Builder {
            this.dbPassword = dbPassword
            return this
        }

        fun driver(dbDriver: String?): Builder {
            this.dbDriver = dbDriver
            return this
        }

        fun build(): Basic {
            return Basic(this)
        }
    }

    @Throws(SQLException::class)
    override fun getConnection(): Connection {
        return try {
            Class.forName(dbDriver)
            val connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)
            connection.autoCommit = false
            this.connection = connection
            connection
        } catch (ex: SQLException) {
            throw RuntimeException("Problem connecting to the database", ex)
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException("Problem connecting to the database", ex)
        }
    }

    @Throws(SQLException::class)
    override fun getConnection(username: String, password: String): Connection {
        return try {
            Class.forName(dbDriver)
            DriverManager.getConnection(dbUrl, username, password)
        } catch (ex: SQLException) {
            throw RuntimeException("Problem connecting to the database", ex)
        } catch (ex: ClassNotFoundException) {
            throw RuntimeException("Problem connecting to the database", ex)
        }
    }

    @Throws(SQLException::class)
    override fun getLogWriter(): PrintWriter {
        return null
    }

    @Throws(SQLException::class)
    override fun setLogWriter(out: PrintWriter) {
    }

    @Throws(SQLException::class)
    override fun setLoginTimeout(seconds: Int) {
        loginTimeout = seconds
    }

    @Throws(SQLException::class)
    override fun getLoginTimeout(): Int {
        return loginTimeout!!
    }

    @Throws(SQLFeatureNotSupportedException::class)
    override fun getParentLogger(): Logger {
        return null
    }

    @Throws(SQLException::class)
    override fun <T> unwrap(iface: Class<T>): T {
        return null
    }

    @Throws(SQLException::class)
    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return false
    }

    companion object {
        private var DB: String? = null
    }

    init {
        dbDriver = builder.dbDriver
        dbUrl = builder.dbUrl
        dbName = builder.dbName
        dbUsername = builder.dbUsername
        dbPassword = builder.dbPassword
        DB = dbName
    }
}