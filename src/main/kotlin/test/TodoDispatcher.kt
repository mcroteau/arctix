package test

import plsar.annotate.*
import plsar.annotate.Dispatcher
import plsar.annotate.verbs.Get
import plsar.annotate.verbs.Post
import plsar.model.web.HttpRequest
import plsar.model.web.HttpResponse
import plsar.util.Support

@Dispatcher
class TodoDispatcher {

    @Inject
    var support: Support? = null

    @Inject
    var todoRepo: TodoRepo? = null

    @Text
    @Get("/text")
    fun text(): String {
        return "Hello World"
    }

    @Get("/")
    @Design("designs/default.htm")
    fun base(resp: HttpResponse): String {
        val todos = todoRepo?.list()
        val keywords = StringBuilder()
        var idx = 1
        for (todo in todos!!) {
            keywords.append(todo.title)
            if (idx < todos.size) {
                keywords.append(",")
            }
            val people = todoRepo!!.getPeople(todo.id)
            todo.people = people
            idx++
        }
        resp.title = "$keywords that need to be done!"
        resp.keywords = keywords.toString()
        resp.description = "$keywords that need to be done!"
        resp.set("todos", todos)
        return "/pages/todo/list.htm"
    }

    @Get("/todos")
    @Design("designs/default.htm")
    fun getList(resp: HttpResponse): String {
        val todos = todoRepo?.list()
        for (todo in todos!!) {
            val people = todoRepo!!.getPeople(todo.id)
            todo.people = people
        }
        resp.set("todos", todos)
        return "/pages/todo/list.htm"
    }

    @Get("/todos/create")
    @Design("designs/default.htm")
    fun getCreate(resp: HttpResponse): String {
        resp.title = "Create"
        resp.keywords = "create todo, todos create"
        resp.description = "create your todo"
        return "/pages/todo/create.htm"
    }

    @Post("/todos/save")
    fun saveTodo(
                req: HttpRequest?,
                resp: HttpResponse?
            ): String {
        val todo = support?.get(req!!, Todo::class.java) as Todo
        todoRepo!!.save(todo)
        resp?.set("message", "successfully added todo!")
        return "[redirect]/todos"
    }

    @Get("/todos/edit/{id}")
    @Design("designs/default.htm")
    fun getEdit(
                resp: HttpResponse?,
                @Variable id: Int
            ): String {
        val todo = todoRepo!!.get(id)
        resp?.set("todo", todo)
        return "/pages/todo/edit.htm"
    }

    @Post("/todos/update/{id}")
    fun updateTodo(
        req: HttpRequest,
        resp: HttpResponse,
        @Variable id: Int
    ): String {
        val todo = todoRepo!!.get(id)
        todo.title = req.value("title")
        todoRepo!!.update(todo)
        resp?.set("message", "successfully updated todo!")
        return "[redirect]/todos/edit/" + todo.id
    }

    @Post("/todos/delete/{id}")
    fun deleteTodo(
        resp: HttpResponse,
        @Variable id: Int
    ): String {
        todoRepo!!.delete(id)
        resp?.set("message", "successfully deleted todo.")
        return "[redirect]/todos"
    }

    @Get("/todos/person/add/{id}")
    @Design("designs/default.htm")
    fun addPersonView(
        resp: HttpResponse,
        @Variable id: Int
    ): String {
        val todo = todoRepo!!.get(id)
        val people = todoRepo!!.getPeople(id)
        resp.set("people", people)
        resp.set("todo", todo)
        return "/pages/todo/add_person.htm"
    }

    @Post("/todos/person/add")
    fun addPerson(
        req: HttpRequest?,
        resp: HttpResponse?
    ): String {
        val todoPerson = support?.get(req!!, TodoPerson::class.java) as TodoPerson
        todoRepo!!.savePerson(todoPerson)
        return "[redirect]/todos/person/add/" + todoPerson.todoId
    }

    @Post("/todos/person/delete/{id}")
    fun deletePerson(
        resp: HttpResponse,
        @Variable id: Int?
    ): String {
        todoRepo!!.deletePerson(id)
        resp.set("message", "successfully deleted person from todo!")
        return "[redirect]/"
    }
}