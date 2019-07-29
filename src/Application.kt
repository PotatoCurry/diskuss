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
import io.ktor.http.content.*
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
        static("assets") {
            resources("assets")
        }

        get {
            call.respondHtml {
                head {
                    title("Diskuss")
                    link("/assets/main.css", "stylesheet")
                }
                body {
                    h1 { a("/") { +"Diskuss" } }
                    h2 { +"Boards" }
                    ul {
                        boards.forEach { board -> // TODO: Add board full name/description - do this when boards become declared in file instead of hardcoded
                            li {
                                p { a(board.name) { +"${board.name} - description" } }
                            }
                        }
                    }

                }
            }
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
                val board = call.attributes[boardKey]
                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val startIndex = min((page - 1) * 10, board.threads.size)
                val endIndex = min(startIndex + 10, board.threads.size)

                call.respondHtml {
                    head {
                        title("/${board.name}/ | Diskuss")
                        link("/assets/main.css", "stylesheet")
                    }
                    body {
                        div("nav") {
                            id = "navbar"
                            a("/", classes = "title") { +"Diskuss" }
                            a("/${board.name}") { +"/${board.name}/" }
                        }
                        a("/${board.name}/submit") { +"Submit a new thread" } // TODO: Move to upper-right
                        div("contain") {
                            board.threads.subList(startIndex, endIndex).forEach { thread ->
                                div("thread") {
                                    id = "t${thread.id}"
                                    p("big") { +"${thread.title} | ${thread.time}" }
                                    p { +thread.text }
                                    p { a("/${board.name}/thread/${thread.id}") { +"${thread.comments.size} comments" } }
                                }
                            }
                        }
                        p {
                            for (i in 1..10)
                                a("/${board.name}/$i") { +"$i " }
                        }
                    }
                }
            }

            route("submit") {
                get {
                    val board = call.attributes[boardKey]

                    call.respondHtml {
                        head {
                            title("New thread | Diskuss")
                            link("/assets/main.css", "stylesheet")
                        }
                        body {
                            div("nav") {
                                id = "navbar"
                                a("/", classes = "title") { +"Diskuss" }
                                a("/${board.name}") { +"/${board.name}/" }
                            }
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
                            title("${thread.title} | Diskuss")
                            link("/assets/main.css", "stylesheet")
                        }
                        body {
                            div("nav") {
                                id = "navbar"
                                a("/", classes = "title") { +"Diskuss" }
                                a("/${board.name}") { +"/${board.name}/" }
                            }
                            div("contain") {
                                div("threadTitle") {
                                    id = "t${thread.id}"
                                    p { +"${thread.title} | ${thread.time}" }
                                    p { +thread.text }
                                }
                                div("commentcontain") {
                                    thread.comments.forEach { comment ->
                                        div("thread") {
                                            id = "c${comment.id}"
                                            p { +"Anonymous | ${comment.time}" }
                                            p { +comment.text }
                                            p { a("#c${comment.id}") { +"Link" } }
                                        }
                                    }
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
