package test

import plsar.annotate.Inject
import plsar.annotate.Dispatcher
import plsar.annotate.Text
import plsar.annotate.verbs.Get
import plsar.model.web.HttpResponse

@Dispatcher
class HelloController {

    @Inject
    val dataRepo : DataRepo? = null

    @Text
    @Get("/hello")
    fun hello() : String {
        return "hello world " + dataRepo?.list();
    }

    @Text
    @Get("/jvm")
    fun jvm() : String {
        return "hello love";
    }
}