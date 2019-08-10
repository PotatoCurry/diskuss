package io.github.potatocurry.diskuss

import io.github.potatocurry.diskuss.Comments.threadId
import io.github.potatocurry.diskuss.Threads.boardId
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class Board(boardRaw: ResultRow) {
    val id = boardRaw[Boards.id].value
    val name = boardRaw[Boards.name]
    val description = boardRaw[Boards.description]
    val threads: List<Thread>
        get() = transaction {
            val threadsRaw = Threads.select { boardId.eq(id) }.toList()
            threadsRaw.map(::Thread)
        }
}

class Thread(threadRaw: ResultRow) {
    val id = threadRaw[Threads.id].value
    val time = threadRaw[Threads.time]
    val title = threadRaw[Threads.title]
    val text = threadRaw[Threads.text]
    val comments: List<Comment>
        get() = transaction {
            val commentsRaw = Comments.select { threadId.eq(id) }.toList()
            commentsRaw.map(::Comment)
        }
}

class Comment(commentRaw: ResultRow) {
    val id = commentRaw[Comments.id].value
    val time = commentRaw[Comments.time]
    val text = commentRaw[Comments.text]
}
