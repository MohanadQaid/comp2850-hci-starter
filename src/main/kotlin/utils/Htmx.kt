package utils

import io.ktor.server.application.*

fun ApplicationCall.isHtmxRequest(): Boolean =
    request.headers["HX-Request"] == "true"
