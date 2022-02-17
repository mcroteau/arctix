package test

import foo.Eos

fun main(args : Array<String>){
    val eos = Eos.Builder().withPort(8080).spawn(1301).make()
    eos.run()
}