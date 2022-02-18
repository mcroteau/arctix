package test

import plsar.Plsar
import plsar.annotate.Data
import plsar.annotate.Inject

@Data
class TodoRepo {

    @Inject
    var repo: Plsar.Repo? = null

    fun count(): Long? {
        val sql = "select count(*) from todos"
        return repo?.getLong(sql, arrayOf<Any?>())
    }

    fun get(id: Int): Todo {
        val sql = "select * from todos where id = [+]"
        return repo?.get(sql, arrayOf<Any?>(id), Todo::class.java) as Todo
    }

    fun list(): List<Todo> {
        val sql = "select * from todos"
        return repo?.getList(sql, arrayOf<Any?>(), Todo::class.java) as ArrayList<Todo>
    }

    fun save(todo: Todo) {
        val sql = "insert into todos (title) values ('[+]')"
        repo?.save(
            sql, arrayOf<Any?>(
                todo.title
            )
        )
    }

    fun update(todo: Todo?) {
        val sql = "update todos set title = '[+]', complete = [+] where id = [+]"
        repo?.update(
            sql, arrayOf<Any?>(
                todo?.title,
                todo!!.isComplete,
                todo.id
            )
        )
    }

    fun delete(id: Int) {
        val sql = "delete from todos where id = [+]"
        repo?.delete(sql, arrayOf<Any?>(id))
    }

    fun getPeople(id: Int?): List<TodoPerson> {
        val sql = "select * from todo_people where todo_id = [+]"
        return repo?.getList(sql, arrayOf<Any?>(id), TodoPerson::class.java) as ArrayList<TodoPerson>
    }

    fun savePerson(todoPerson: TodoPerson) {
        val sql = "insert into todo_people (todo_id, person) values ([+],'[+]')"
        repo?.save(
            sql, arrayOf<Any?>(
                todoPerson.todoId,
                todoPerson.person
            )
        )
    }

    fun deletePerson(id: Int?) {
        val sql = "delete from todo_people where id = [+]"
        repo?.delete(sql, arrayOf<Any?>(id))
    }
}