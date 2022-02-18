package test

import plsar.Plsar
import plsar.annotate.Data
import plsar.annotate.Inject

@Data
class DataRepo {

    @Inject
    val repo : Plsar.Repo? = null

    fun list() : Int? {
        val sql : String = "select count(*) from bebops"
        return repo?.getInteger(sql, arrayOf<Any?>())
    }

}