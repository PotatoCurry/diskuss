package io.github.potatocurry.diskuss

import io.github.potatocurry.diskuss.Boards.name
import io.github.potatocurry.diskuss.Manager.boards
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.html.insert
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.resources
import io.ktor.http.content.static
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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import javax.management.Query.eq
import kotlin.math.min

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val database = Database.connect(System.getenv("JDBC_DATABASE_URL"), "org.postgresql.Driver")

    transaction {
        addLogger(StdOutSqlLogger)

        importBoards().forEach { boardName ->
            if (Boards.select { name.eq(boardName) }.empty())
                Boards.insert {
                    it[name] = boardName
                }
        }

    }

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
                    div("nav") {
                        id = "navbar"
                        a("/", classes = "title") { +"Diskuss" }
                    }
                    div("contain") {
                        h2 { +"Boards" }
                        ul {
                            boards.forEach { board ->
                                // TODO: Add board full name/description - do this when boards become declared in file instead of hardcoded
                                li {
                                    p { a(board.name) { +"${board.name} - description" } }
                                }
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
                val threads = board.threads
                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val startIndex = min((page - 1) * 10, threads.size)
                val endIndex = min(startIndex + 10, threads.size)

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
                            a("/${board.name}/submit", classes = "right") { +"Submit" }
                        }
                        div("contain") {
                            board.threads.subList(startIndex, endIndex).forEach { thread ->
                                div("thread") {
                                    id = "t${thread.id}"
                                    p("big") { +"${thread.title} | ${thread.time}" }
                                    p { +thread.text }
                                    p { a("/${board.name}/thread/${thread.id}") { +"${thread.comments.size} comments" } }
                                }
                                br
                            }

                            p("pagerp") {
                                if (page == 1)
                                    a("/${board.name}/$1", classes = "active roundL") { +"1 " }
                                else
                                    a("/${board.name}/$1", classes = "pager roundL") { +"1 " }

                                for (i in 2..9) //make first and last element have rounded corner
                                    if (i == page)
                                        a("/${board.name}/$i", classes = "active") { +"$i " }
                                    else
                                        a("/${board.name}/$i", classes = "pager") { +"$i " }

                                if (page == 10)
                                    a("/${board.name}/$10", classes = "active roundR") { +"10 " }
                                else
                                    a("/${board.name}/$10", classes = "pager roundR") { +"10 " }
                            }
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
                                a("/${board.name}/submit", classes = "right") { +"Submit" }
                            }
                            div("contain"){
                                form(method = FormMethod.post) {
                                    acceptCharset = "utf-8"
                                    p("big") {
                                        label { +"Title: " }
                                        textInput(classes="textbox"){ name = "title" }
                                    }
                                    p("big") {
                                        label { +"Text: " }
                                        textInput (classes="textbox"){ name = "text" }
                                    }
                                    submitInput(classes="button") { value = "Send" }
                                }
                            }
                        }
                    }
                }

                post {
                    val submission = call.receiveParameters()

                    if (validateThread(submission)) {
                        val board = call.attributes[boardKey]
                        Manager.insertThread(
                            board,
                            submission["title"]!!,
                            submission["text"]!!
                        ).also {
                            call.respondRedirect("thread/${it[Threads.id]}")
                        }
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
                                a(href = "/${board.name}/submit", classes = "right") { +"Submit" }
                            }

                            div("contain") {
                                br
                                br
                                div("threadTitle") {
                                    id = "t${thread.id}"
                                    h1 { +thread.title }
                                    h2 { +"Anonymous | ${thread.time}" }
                                    p { +thread.text }
                                }
                                br
                                div("commentcontain") {
                                    thread.comments.forEach { comment ->
                                        div("thread") {
                                            id = "c${comment.id}"
                                            p { +"Anonymous | ${comment.time}" }
                                            p { +comment.text }
                                            p { a("#c${comment.id}") { +"Link" } }
                                        }
                                        br
                                    }
                                }

                                p { +"Comment" }
                                form(method = FormMethod.post) {
                                    acceptCharset = "utf-8"
                                    p {
                                        label { +"Text: " }
                                        textInput (classes="textbox") { name = "text" }
                                    }
                                    submitInput(classes = "button") { value = "send" }
                                }
                            }
                        }
                    }
                }

                post {
                    val submission = call.receiveParameters()

                    if (validateComment(submission)) {
                        val thread = call.attributes[threadKey]
                        Manager.insertComment(
                            thread,
                            submission["text"]!!
                        ).also {
                            call.respondRedirect("#c${it[Comments.id]}")
                        }
                    } else {
                        call.respondText("Invalid registration data", status = HttpStatusCode.UnprocessableEntity)
                    }
                }
            }
        }
    }
}

private fun importBoards(): List<String> {
    return listOf("abc", "test")
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
