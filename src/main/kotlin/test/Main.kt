package test

import plsar.Plsar

fun main(){
    val plsar = Plsar.Builder()
                .port(8080)
                .spawn(1301)
                .create()
    plsar.run()
}