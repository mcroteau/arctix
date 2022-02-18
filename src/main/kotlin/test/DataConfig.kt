package test

import plsar.annotate.Config
import plsar.annotate.Dependency
import plsar.annotate.Property
import plsar.jdbc.datasource.Basic
import javax.sql.DataSource

@Config
class DataConfig {

    @Property("db.user")
    val user : String ? = null

    @Property("db.pass")
    val pass : String ? = null

    @Property("db.url")
    val url : String ? = null

    @Property("db.driver")
    val driver : String ? = null

    @Dependency
    fun dataSource() : DataSource? {
        return Basic.Builder()
            .driver(driver)
            .url(url)
            .username(user)
            .password(pass)
            .build()
    }
}