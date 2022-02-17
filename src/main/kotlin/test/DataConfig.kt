package test

import eos.annotate.Config
import eos.annotate.Dependency
import eos.annotate.Property
import eos.jdbc.datasource.Basic
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