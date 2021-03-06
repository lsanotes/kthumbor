package me.hltj.kthumbor

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import java.util.concurrent.atomic.AtomicLong

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

fun Application.module() {
    install(CallId) {
        header(HttpHeaders.XRequestId)

        val counter = AtomicLong()
        generate { "hltj-me-${counter.incrementAndGet()}" }
    }

    install(CallLogging) {
        callIdMdc("request-id")
    }

    routing {
        get("/") {
            call.respondText("Kthumbor - a thumbnail service")
        }

        get("/{path...}") {
            val path = call.request.path()
            if (path.count { ch -> ch == '.' } < 2) {
                call.resolveResource(path.removePrefix("/"), "static")?.let { content ->
                    call.respond(content)
                } ?: call.respondStatus(HttpStatusCode.NotFound)
            } else when (val result = path fetchWith ::httpImageOf) {
                KthumborResult.BadInput -> call.respondStatus(HttpStatusCode.BadRequest)
                KthumborResult.NotFound -> call.respondStatus(HttpStatusCode.NotFound)
                is KthumborResult.Success -> call.respondOutputStream {
                    this += result.value
                }
                is KthumborResult.Failure -> {
                    call.application.log.error("failure - $path - ", result.exception)
                    call.respondStatus(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    respondText(status.description, status = status)
}