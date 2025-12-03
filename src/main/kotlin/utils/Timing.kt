package utils

import io.ktor.server.application.*
import io.ktor.util.*

val RequestIdKey = AttributeKey<String>("RequestId")

fun newReqId(): String = "r" + System.currentTimeMillis().toString()

fun ApplicationCall.jsMode(): String =
    if (request.headers["HX-Request"]?.equals("true", true) == true) "on" else "off"

suspend fun ApplicationCall.timed(
    taskCode: String,
    jsMode: String,
    block: suspend ApplicationCall.() -> Unit
) {
    val start = System.currentTimeMillis()

    val session = request.cookies["sid"] ?: "anon"
    val requestId = attributes.getOrNull(RequestIdKey) ?: newReqId()

    try {
        block()
        val duration = System.currentTimeMillis() - start
        Logger.success(session, requestId, taskCode, duration, jsMode)

    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - start
        Logger.write(
            LogEntry(
                session, requestId, taskCode,
                "server_error", e.message ?: "unknown",
                duration, 500, jsMode
            )
        )
        throw e
    }
}
