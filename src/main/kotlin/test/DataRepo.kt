package test

import eos.annotate.Bind
import eos.annotate.DataStore
import eos.jdbc.Repo

@DataStore
class DataRepo {

    @Bind
    val repo : Repo? = null

    fun list() : Int? {
        val sql : String = "select count(*) from bebops"
        return repo?.getInteger(sql, arrayOf<Any?>())
    }

}