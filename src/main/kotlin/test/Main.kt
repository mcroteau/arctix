package test

import eos.Eos

fun main(){
    val eos = Eos.Builder().withPort(8080).spawn(1301).create()
    eos.run()
}