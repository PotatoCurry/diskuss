package io.github.potatocurry.diskuss

object Manager {
    private var id = 0

    val nextId: Int
        get() {
            return id++
        }

    val boards = listOf( // Move to config file
        Board("abc"),
        Board("test")
    )
}
