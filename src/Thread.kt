package io.github.potatocurry.diskuss

import java.time.LocalDateTime

// TODO: encapsulate these in single abstract class/interface since they share a lot of properties?

data class Thread(val title: String, val text: String) {
    val id = Manager.nextId
    val time = LocalDateTime.now()
    val comments = mutableListOf<Comment>()
}

data class Comment(val text: String) {
    val id = Manager.nextId
    val time = LocalDateTime.now()
}
