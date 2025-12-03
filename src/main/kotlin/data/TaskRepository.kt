package data

import model.Task
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/**
 * Week 8: TaskStore using String UUID IDs.
 * Stores tasks in-memory + CSV persistence.
 */
object TaskRepository {

    private val file = File("data/tasks.csv")
    private val tasks = mutableListOf<Task>()

    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText("id,title,completed,createdAt\n")
        } else {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",", limit = 4)
                if (parts.size == 4) {
                    val id = parts[0]
                    val title = parts[1]
                    val completed = parts[2].toBoolean()
                    val createdAt = try {
                        LocalDateTime.parse(parts[3])
                    } catch (e: Exception) {
                        LocalDateTime.now()
                    }
                    tasks.add(Task(id = id, title = title, completed = completed, createdAt = createdAt))
                }
            }
        }
    }

    /** Return all tasks sorted newest → oldest */
    fun all(): List<Task> =
        tasks.sortedByDescending { it.createdAt }

    /** Add a new task */
    fun add(title: String): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            completed = false,
            createdAt = LocalDateTime.now()
        )
        tasks.add(task)
        persist()
        return task
    }

    /** Find by String ID */
    fun find(id: String): Task? =
        tasks.find { it.id == id }

    /** Update title or completed state */
    fun update(task: Task) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            tasks[index] = task
            persist()
        }
    }

    /** Delete by String ID */
    fun delete(id: String): Boolean {
        val removed = tasks.removeIf { it.id == id }
        if (removed) persist()
        return removed
    }

    /** Week 8: Search by query */
    fun search(query: String): List<Task> {
        if (query.isBlank()) return all()
        val q = query.lowercase()
        return tasks.filter { it.title.lowercase().contains(q) }
            .sortedByDescending { it.createdAt }
    }

    /** Persist to CSV */
    private fun persist() {
        file.writeText(
            "id,title,completed,createdAt\n" +
                tasks.joinToString("\n") {
                    "${it.id},${it.title},${it.completed},${it.createdAt}"
                }
        )
    }
}
