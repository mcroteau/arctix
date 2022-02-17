package test

import eos.annotate.Bind
import eos.annotate.HttpHandler
import eos.annotate.Plain
import eos.annotate.Text
import eos.annotate.verbs.Get
import eos.model.web.HttpResponse

@HttpHandler
class HelloController {

    @Bind
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