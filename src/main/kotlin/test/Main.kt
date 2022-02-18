package test

import plsar.PLSAR

fun main(){
    val plsar = PLSAR.Builder()
                .port(8080)
                .spawn(1301)
                .create()
    plsar.run()
}