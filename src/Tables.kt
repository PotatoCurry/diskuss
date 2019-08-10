package io.github.potatocurry.diskuss

import org.jetbrains.exposed.dao.IntIdTable

object Boards : IntIdTable() {
    val name = varchar("name", 10)
    val description = text("description")
}

object Threads : IntIdTable() {
    val boardId = (integer("board_id")
        .uniqueIndex()
        .references(Boards.id))
        .entityId()
    val time = datetime("time")
    val pinned = bool("pinned")
    val title = varchar("title", 50)
    val text = text("text")
}

object Comments : IntIdTable() {
    val threadId = (integer("thread_id")
        .uniqueIndex()
        .references(Threads.id))
        .entityId()
    val time = datetime("time")
    val text = text("text")
}
