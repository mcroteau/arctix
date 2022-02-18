package test

import plsar.Pulsar

fun main(){
    val plsar = Pulsar.Builder()
                .port(8080)
                .spawn(1301)
                .create()
    plsar.run()
}