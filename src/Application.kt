package io.github.potatocurry.diskuss

import io.github.potatocurry.diskuss.Manager.boards
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.request.receiveParameters
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.netty.EngineMain
import io.ktor.util.AttributeKey
import kotlinx.html.*
import kotlin.math.min

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    install(CallLogging)

    routing {
        get {
            call.respondText("Welcome to diskuss")
        }

        route("{board}") {
            val boardKey = AttributeKey<Board>("board")

            intercept(ApplicationCallPipeline.Call) {
                val name = call.parameters["board"]
                val board = boards.singleOrNull { it.name.equals(name, true) }
                if (board == null) {
                    call.respondText("The specified board could not be found", status = HttpStatusCode.NotFound)
                    return@intercept finish()
                } else
                    call.attributes.put(boardKey, board)
            }

            get("{page?}") {
                with (call.attributes[boardKey]) {
                    val page = call.parameters["page"]?.toIntOrNull() ?: 1
                    val startIndex = min((page - 1) * 10, threads.size)
                    val endIndex = min(startIndex + 10, threads.size)

                    call.respondHtml {
                        head {
                            title { +"/$name/ | Diskuss" }
                        }
                        body {
                            h1 { +"/$name/ | Diskuss" }
                            p { a("/$name/submit") { +"Submit a new thread" } }
                            threads.subList(startIndex, endIndex).forEach { thread ->
                                div {
                                    id = "t${thread.id}"
                                    p { +"${thread.title} | ${thread.time}" }
                                    p { +thread.text }
                                    p { a("thread/${thread.id}") { +"Thread" } }
                                }
                            }
                            p {
                                for (i in 1..10)
                                    a("/$name/$i") { +"$i " }
                            }
                        }
                    }
                }
            }

            route("submit") {
                get {
                    call.respondHtml {
                        head {
                            title { +"New thread | Diskuss" }
                        }
                        body {
                            p { +"Submit a new thread" }
                            form(method = FormMethod.post) {
                                acceptCharset = "utf-8"
                                p {
                                    label { +"Title: " }
                                    textInput { name = "title" }
                                }
                                p {
                                    label { +"Text: " }
                                    textInput { name = "text" }
                                }
                                p {
                                    submitInput { value = "send" }
                                }
                            }
                        }
                    }
                }

                post {
                    val submission = call.receiveParameters()

                    if (validateThread(submission)) {
                        val board = call.attributes[boardKey]
                        board.add(
                            Thread(
                                submission["title"]!!,
                                submission["text"]!!
                            ).also {
                                call.respondRedirect("thread/${it.id}")
                            }
                        )
                    } else {
                        call.respondText("Invalid registration data", status = HttpStatusCode.UnprocessableEntity)
                    }
                }
            }

            route("thread/{id}") {
                val threadKey = AttributeKey<Thread>("thread")

                intercept(ApplicationCallPipeline.Call) {
                    val id = call.parameters["id"]?.toInt()
                    val board = call.attributes[boardKey]
                    val thread = board.threads.singleOrNull { id == it.id }
                    if (thread == null) {
                        call.respondText("The specified thread could not be found", status = HttpStatusCode.NotFound)
                        return@intercept finish()
                    } else
                        call.attributes.put(threadKey, thread)
                }

                get {
                    val board = call.attributes[boardKey]
                    val thread = call.attributes[threadKey]
                    call.respondHtml {
                        head {
                            title { +"${thread.title} | Diskuss" }
                        }
                        body {
                            h1 { +"/${board.name}/ | Diskuss" }
                            div {
                                id = "t${thread.id}"
                                p { +"${thread.title} | ${thread.time}" }
                                p { +thread.text }
                            }
                            thread.comments.forEach { comment ->
                                div {
                                    id = "c${comment.id}"
                                    p { +"Anonymous | ${comment.time}" }
                                    p { +comment.text }
                                    p { a("#c${comment.id}") { +"Link" } }
                                }
                            }

                            p { +"Submit a comment" }
                            form(method = FormMethod.post) {
                                acceptCharset = "utf-8"
                                p {
                                    label { +"Text: " }
                                    textInput { name = "text" }
                                }
                                p {
                                    submitInput { value = "send" }
                                }
                            }
                        }
                    }
                }

                post {
                    val submission = call.receiveParameters()

                    if (validateComment(submission)) {
                        val thread = call.attributes[threadKey]
                        thread.comments.add(
                            Comment(
                                submission["text"]!!
                            ).also {
                                call.respondRedirect("#c${it.id}")
                            }
                        )
                    } else {
                        call.respondText("Invalid registration data", status = HttpStatusCode.UnprocessableEntity)
                    }
                }
            }
        }
    }
}

private fun validateThread(submission: Parameters): Boolean {
    return try {
        requireNotNull(submission["title"])
        requireNotNull(submission["text"])
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}

private fun validateComment(submission: Parameters): Boolean {
    return try {
        requireNotNull(submission["text"])
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}
