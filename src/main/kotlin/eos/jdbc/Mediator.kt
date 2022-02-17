package eos.jdbc

import eos.Eos
import eos.util.*
import org.h2.tools.RunScript
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.StringReader
import javax.sql.DataSource

class Mediator(var settings: Settings?,
               var support: Support,
               var cache: Eos.Cache?) {

    val CREATEDB_URI = "src/main/resources/create-db.sql"

    @Throws(Exception::class)
    fun createDb() {
        val artifactPath: String = Support.Companion.resourceUri
        if (!settings!!.isNoAction &&
            settings!!.isCreateDb
        ) {
            val createSql: StringBuilder?
            createSql = if (support.isJar) {
                val jarFile = support.jarFile
                val jarEntry = jarFile!!.getJarEntry(CREATEDB_URI)
                val `in` = jarFile.getInputStream(jarEntry)
                support.convert(`in`)
            } else {
                val createFile = File(artifactPath + File.separator + "create-db.sql")
                val `in`: InputStream = FileInputStream(createFile)
                support.convert(`in`)
            }
            val datasource = cache!!.getElement("datasource") as DataSource
                ?: throw Exception(
                    """

           You have a8i.env set to create or create,drop in a8i.props.
           In addition you need to configure a datasource. 
           Feel free to use a8i.jdbc.datasource.Basic to get started.
           You can also checkout HikariCP, it is great!

           https://github.com/brettwooldridge/HikariCP


"""
                )
            val conn = datasource.connection
            if (settings!!.isDropDb) {
                RunScript.execute(conn, StringReader("drop all objects;"))
            }
            RunScript.execute(conn, StringReader(createSql.toString()))
            conn.commit()
            conn.close()
        }
    }

    fun dropDb() {
        if (!settings!!.isNoAction &&
            settings!!.isCreateDb
        ) {
            try {
                val datasource = cache!!.getElement("datasource") as DataSource
                val conn = datasource.connection
                RunScript.execute(conn, StringReader("drop all objects;"))
                conn.commit()
                conn.close()
            } catch (e: Exception) {
            }
        }
    }
}