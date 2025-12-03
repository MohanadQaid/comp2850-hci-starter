package routes

import data.TaskRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.PebbleEngine
import java.io.StringWriter
import utils.Page
import utils.RequestIdKey       // WEEK 9
import utils.jsMode            // WEEK 9
import utils.newReqId          // WEEK 9
import utils.timed             // WEEK 9
import utils.Logger            // WEEK 9
import utils.LogEntry          // WEEK 9

fun Route.taskRoutes() {

    val pebble =
        PebbleEngine.Builder()
            .loader(
                io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                    prefix = "templates/"
                },
            ).build()

    fun ApplicationCall.isHtmx(): Boolean =
        request.headers["HX-Request"]?.equals("true", ignoreCase = true) == true

    // -----------------------------
    // GET /tasks  (FULL PAGE)
    // -----------------------------
    get("/tasks") {

        // WEEK 9 — assign request ID
        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        // WEEK 9 — timed logging
        call.timed("T1_filter", js) {

            val query = call.request.queryParameters["q"].orEmpty()
            val pageNumber = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

            val tasks = TaskRepository.search(query)
            val paged = Page.paginate(tasks, currentPage = pageNumber, pageSize = 10)

            val model = mapOf(
                "title" to "Tasks",
                "page" to paged,
                "query" to query
            )
            val template = pebble.getTemplate("tasks/index.peb")
            val writer = StringWriter()
            template.evaluate(writer, model)

            call.respondText(writer.toString(), ContentType.Text.Html)
        }
    }


    // -----------------------------
    // GET /tasks/fragment  (HTMX ONLY)
    // -----------------------------
    get("/tasks/fragment") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        call.timed("T1_filter", js) {

            val query = call.request.queryParameters["q"].orEmpty()
            val pageNumber = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

            val tasks = TaskRepository.search(query)
            val paged = Page.paginate(tasks, currentPage = pageNumber, pageSize = 10)

            val listHtml = renderFragment(pebble, "tasks/_list.peb", mapOf("page" to paged, "query" to query))
            val pagerHtml = renderFragment(pebble, "tasks/_pager.peb", mapOf("page" to paged, "query" to query))

            val oobStatus = """<div id="status" hx-swap-oob="true">Found ${paged.totalItems} tasks.</div>"""

            call.respondText(listHtml + pagerHtml + oobStatus, ContentType.Text.Html)
        }
    }


    // -----------------------------
    // POST /tasks  (CREATE)
    // -----------------------------
    post("/tasks") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()
        val reqId = call.attributes[RequestIdKey]
        val session = call.request.cookies["sid"] ?: "anon"

        call.timed("T3_add", js) {

            val title = call.receiveParameters()["title"].orEmpty().trim()

            if (title.isBlank()) {
                // WEEK 9 LOGGING
                Logger.validationError(session, reqId, "T3_add", "blank_title", js)

                if (call.isHtmx()) {
                    val err = """<div id="status" hx-swap-oob="true">Title is required.</div>"""
                    return@timed call.respondText(err, ContentType.Text.Html, HttpStatusCode.BadRequest)
                }
                return@timed call.respondRedirect("/tasks")
            }

            val task = TaskRepository.add(title)

            if (call.isHtmx()) {
                val fragment = """
                    <li id="task-${task.id}">
                        <span>${task.title}</span>
                        <form action="/tasks/${task.id}/delete" method="post"
                              hx-post="/tasks/${task.id}/delete"
                              hx-target="#task-${task.id}"
                              hx-swap="outerHTML">
                          <button type="submit">Delete</button>
                        </form>
                    </li>
                """
                val status = """<div id="status" hx-swap-oob="true">Task "${task.title}" added.</div>"""
                return@timed call.respondText(fragment + status, ContentType.Text.Html)
            }

            return@timed call.respondRedirect("/tasks")
        }
    }


    // -----------------------------
    // POST /tasks/{id}/delete
    // -----------------------------
    post("/tasks/{id}/delete") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        call.timed("T4_delete", js) {

            val id = call.parameters["id"] ?: return@timed call.respond(HttpStatusCode.BadRequest)
            val removed = TaskRepository.delete(id)

            if (call.isHtmx()) {
                val msg = if (removed) "Task deleted." else "Could not delete task."
                val status = """<div id="status" hx-swap-oob="true">$msg</div>"""
                return@timed call.respondText(status, ContentType.Text.Html)
            }

            call.respondRedirect("/tasks")
        }
    }


    // -----------------------------
    // GET /tasks/{id}/edit
    // -----------------------------
    get("/tasks/{id}/edit") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        call.timed("T2_edit", js) {

            val id = call.parameters["id"] ?: return@timed call.respond(HttpStatusCode.NotFound)
            val task = TaskRepository.find(id) ?: return@timed call.respond(HttpStatusCode.NotFound)
            val error = call.request.queryParameters["error"]

            if (call.isHtmx()) {
                val w = renderFragmentWriter(pebble, "tasks/_edit.peb", mapOf("task" to task, "error" to error))
                return@timed call.respondText(w.toString(), ContentType.Text.Html)
            }

            val all = TaskRepository.all()
            val template = pebble.getTemplate("tasks/index.peb")
            val writer = StringWriter()
            template.evaluate(writer, mapOf(
                "title" to "Tasks",
                "page" to Page.paginate(all, 1, 10),
                "editingId" to id,
                "errorMessage" to error
            ))
            call.respondText(writer.toString(), ContentType.Text.Html)
        }
    }


    // -----------------------------
    // POST /tasks/{id}/edit
    // -----------------------------
    post("/tasks/{id}/edit") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()
        val reqId = call.attributes[RequestIdKey]
        val session = call.request.cookies["sid"] ?: "anon"

        call.timed("T2_edit", js) {

            val id = call.parameters["id"] ?: return@timed call.respond(HttpStatusCode.NotFound)
            val task = TaskRepository.find(id) ?: return@timed call.respond(HttpStatusCode.NotFound)

            val newTitle = call.receiveParameters()["title"].orEmpty().trim()

            if (newTitle.isBlank()) {

                Logger.validationError(session, reqId, "T2_edit", "blank_title", js)

                if (call.isHtmx()) {
                    val w = renderFragmentWriter(pebble, "tasks/_edit.peb",
                        mapOf("task" to task, "error" to "Title is required.")
                    )
                    return@timed call.respondText(w.toString(), ContentType.Text.Html, HttpStatusCode.BadRequest)
                }
                return@timed call.respondRedirect("/tasks/${id}/edit?error=blank")
            }

            val updated = task.copy(title = newTitle)
            TaskRepository.update(updated)

            if (call.isHtmx()) {
                val item = renderFragment(pebble, "tasks/_item.peb", mapOf("task" to updated))
                val status = """<div id="status" hx-swap-oob="true">Updated "${updated.title}".</div>"""
                return@timed call.respondText(item + status, ContentType.Text.Html)
            }

            call.respondRedirect("/tasks")
        }
    }


    // -----------------------------
    // GET /tasks/{id}/view  (cancel edit)
    // -----------------------------
    get("/tasks/{id}/view") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        call.timed("T2_edit", js) {

            val id = call.parameters["id"] ?: return@timed call.respond(HttpStatusCode.NotFound)
            val task = TaskRepository.find(id) ?: return@timed call.respond(HttpStatusCode.NotFound)

            val fragment = renderFragment(pebble, "tasks/_item.peb", mapOf("task" to task))
            call.respondText(fragment, ContentType.Text.Html)
        }
    }
}


private fun renderFragment(pebble: PebbleEngine, template: String, model: Map<String, Any>): String {
    val t = pebble.getTemplate(template)
    val w = StringWriter()
    t.evaluate(w, model)
    return w.toString()
}

private fun renderFragmentWriter(pebble: PebbleEngine, template: String, model: Map<String, Any>): StringWriter {
    val t = pebble.getTemplate(template)
    val w = StringWriter()
    t.evaluate(w, model)
    return w
}
