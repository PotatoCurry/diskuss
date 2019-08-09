package io.github.potatocurry.diskuss

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object Manager {
    val boards: List<Board> = transaction {
        Boards.selectAll().map(::Board)
    }

    fun insertThread(board: Board, threadTitle: String, threadText: String): InsertStatement<Number> {
        return transaction {
            Threads.insert {
                it[boardId] = EntityID(board.id, Boards)
                it[time] = DateTime.now()
                it[title] = threadTitle
                it[text] = threadText
            }
        }
    }

    fun insertComment(thread: Thread, commentText: String): InsertStatement<Number> {
        return transaction {
            Comments.insert {
                it[threadId] = EntityID(thread.id, Threads)
                it[time] = DateTime.now()
                it[text] = commentText
            }
        }
    }
}
