package model

import java.time.LocalDateTime

data class Task(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
