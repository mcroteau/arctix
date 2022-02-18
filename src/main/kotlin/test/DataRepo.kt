package test

import plsar.annotate.Inject

@Repo
class DataRepo {

    @Inject
    val repo : Repo? = null

    fun list() : Int? {
        val sql : String = "select count(*) from bebops"
        return repo?.getInteger(sql, arrayOf<Any?>())
    }

}