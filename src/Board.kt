package io.github.potatocurry.diskuss

class Board(val name: String) { // TODO: add shortName?
    val threads = mutableListOf<Thread>()

    fun add(thread: Thread) {
        if (threads.size > 100)
            threads.remove(threads.minBy { it.comments.size })
        threads += thread
    }
}
