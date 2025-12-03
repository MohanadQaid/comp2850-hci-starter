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
import utils.RequestIdKey
import utils.jsMode
import utils.newReqId
import utils.timed
import utils.Logger
import utils.isHtmxRequest   // <-- correct import

fun Route.taskRoutes() {

    val pebble = PebbleEngine.Builder()
        .loader(io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
            prefix = "templates/"
        }).build()

    // -----------------------------
    // GET /tasks  (FULL PAGE)
    // -----------------------------
    get("/tasks") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        call.timed("T1_filter", js) {

            val query = call.request.queryParameters["q"].orEmpty()
            val pageNumber = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

            val tasks = TaskRepository.search(query)
            val paged = Page.paginate(tasks, currentPage = pageNumber, pageSize = 10)

            val writer = StringWriter()
            val template = pebble.getTemplate("tasks/index.peb")
            template.evaluate(writer, mapOf(
                "title" to "Tasks",
                "page" to paged,
                "query" to query
            ))

            call.respondText(writer.toString(), ContentType.Text.Html)
        }
    }

    // -----------------------------
    // GET /tasks/fragment (HTMX)
    // -----------------------------
    get("/tasks/fragment") {

        call.attributes.put(RequestIdKey, newReqId())
        val js = call.jsMode()

        call.timed("T1_filter", js) {

            val query = call.request.queryParameters["q"].orEmpty()
            val pageNumber = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

            val tasks = TaskRepository.search(query)
            val paged = Page.paginate(tasks, currentPage = pageNumber, pageSize = 10)

            val listHtml = renderFragment(pebble, "tasks/_list.peb", mapOf(
                "page" to paged, "query" to query
            ))

            val pagerHtml = renderFragment(pebble, "tasks/_pager.peb", mapOf(
                "page" to paged, "query" to query
            ))

            val status = """<div id="status" hx-swap-oob="true">Found ${paged.totalItems} tasks.</div>"""

            call.respondText(listHtml + pagerHtml + status, ContentType.Text.Html)
        }
    }

    // -----------------------------
    // POST /tasks (CREATE)
    // -----------------------------
    post("/tasks") {

        val params = call.receiveParameters()
        val title = params["title"]?.trim().orEmpty()

        if (title.isBlank()) {
            call.respondRedirect("/tasks?error=Title+required")
            return@post
        }

        TaskRepository.add(title)

        if (call.isHtmxRequest()) {
            val tasks = TaskRepository.all()
            val paged = Page.paginate(tasks, 1, 10)

            val listHtml = renderFragment(pebble, "tasks/_list.peb", mapOf("page" to paged))
            val pagerHtml = renderFragment(pebble, "tasks/_pager.peb", mapOf("page" to paged))

            val headingOob = """
                <div id="list-heading" hx-swap-oob="innerHTML">
                    Current tasks (${paged.totalItems})
                </div>
            """.trimIndent()

            val statusOob = """
                <div id="status" hx-swap-oob="true">
                    Added "${title}"
                </div>
            """.trimIndent()

            call.respondText(listHtml + pagerHtml + headingOob + statusOob, ContentType.Text.Html)
            return@post
        }


        call.respondRedirect("/tasks")
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

            if (call.isHtmxRequest()) {
                val tasks = TaskRepository.all()
                val paged = Page.paginate(tasks, 1, 10)

                val headingOob = """
                    <div id="list-heading" hx-swap-oob="innerHTML">
                        Current tasks (${paged.totalItems})
                    </div>
                """

                val statusOob = """
                    <div id="status" hx-swap-oob="true">
                        Task deleted.
                    </div>
                """

                return@timed call.respondText(headingOob + statusOob, ContentType.Text.Html)
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

            if (call.isHtmxRequest()) {
                val w = renderFragmentWriter(
                    pebble,
                    "tasks/_edit.peb",
                    mapOf("task" to task, "error" to error.orEmpty())
                )
                return@timed call.respondText(w.toString(), ContentType.Text.Html)
            }

            val all = TaskRepository.all()
            val writer = StringWriter()
            pebble.getTemplate("tasks/index.peb").evaluate(writer, mapOf(
                "title" to "Tasks",
                "page" to Page.paginate(all, 1, 10),
                "editingId" to id,
                "errorMessage" to error.orEmpty()
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

        call.timed("T2_edit", js) {

            val id = call.parameters["id"] ?: return@timed call.respond(HttpStatusCode.NotFound)
            val task = TaskRepository.find(id) ?: return@timed call.respond(HttpStatusCode.NotFound)
            val newTitle = call.receiveParameters()["title"].orEmpty().trim()

            if (newTitle.isBlank()) {

                if (call.isHtmxRequest()) {
                    val html = renderFragmentWriter(
                        pebble,
                        "tasks/_edit.peb",
                        mapOf("task" to task, "error" to "Title is required.")
                    )
                    return@timed call.respondText(html.toString(), ContentType.Text.Html, HttpStatusCode.BadRequest)
                }

                return@timed call.respondRedirect("/tasks/$id/edit?error=blank")
            }

            val updated = task.copy(title = newTitle)
            TaskRepository.update(updated)

            if (call.isHtmxRequest()) {
                val itemHtml = renderFragment(pebble, "tasks/_item.peb", mapOf("task" to updated))
                val status = """<div id="status" hx-swap-oob="true">Updated "${updated.title}".</div>"""
                return@timed call.respondText(itemHtml + status, ContentType.Text.Html)
            }

            call.respondRedirect("/tasks")
        }
    }

    // -----------------------------
    // GET /tasks/{id}/view (cancel edit)
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
    val w = StringWriter()
    pebble.getTemplate(template).evaluate(w, model)
    return w.toString()
}

private fun renderFragmentWriter(pebble: PebbleEngine, template: String, model: Map<String, Any>): StringWriter {
    val w = StringWriter()
    pebble.getTemplate(template).evaluate(w, model)
    return w
}
