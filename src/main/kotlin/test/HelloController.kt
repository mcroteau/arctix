package test

import plsar.annotate.Inject
import plsar.annotate.HttpHandler
import plsar.annotate.Text
import plsar.annotate.verbs.Get
import plsar.model.web.HttpResponse

@HttpHandler
class HelloController {

    @Inject
    val dataRepo : DataRepo? = null

    @Text
    @Get("/")
    fun hello() : String {
        return "hello world " + dataRepo?.list();
    }

    @Text
    @Get("/jvm")
    fun jvm(resp: HttpResponse) : String {
        return "hello love";
    }
}