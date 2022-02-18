package test

import plsar.Plsar

fun main(){
    val eos = Plsar.Builder().withPort(8080).spawn(1301).create()
    eos.run()
}