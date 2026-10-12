package sk.kvz.priestor.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/** Podsekcia v rámci oblasti, napr. Chata → „Nakúpiť“, „Urobiť“. */
data class Section(val id: String, val name: String)

/** Oblasť (Domov, Nákup, Dovolenka…). Vidia ju iba jej členovia. */
data class Space(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<String>,
    val memberNames: Map<String, String>,
    val sections: List<Section>,
    val color: Int,
) {
    fun sectionName(sectionId: String?): String? = sections.firstOrNull { it.id == sectionId }?.name
}

data class TaskItem(
    val id: String,
    val spaceId: String,
    val title: String,
    val note: String,
    val sectionId: String?,
    /** Termín v ms. Ak [hasTime] je false, je to polnoc daného dňa (lokálny čas). */
    val dueAt: Long?,
    val hasTime: Boolean,
    val done: Boolean,
    val doneAt: Long?,
    val doneByName: String?,
    val createdByName: String?,
    val createdAt: Long,
)

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toSpace(): Space? {
    val name = getString("name") ?: return null
    val sections = (get("sections") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
        val id = m["id"] as? String ?: return@mapNotNull null
        Section(id, m["name"] as? String ?: "")
    }
    return Space(
        id = id,
        name = name,
        ownerId = getString("ownerId") ?: "",
        members = (get("members") as? List<String>).orEmpty(),
        memberNames = (get("memberNames") as? Map<String, String>).orEmpty(),
        sections = sections,
        color = (getLong("color") ?: 0L).toInt(),
    )
}

internal fun DocumentSnapshot.toTask(spaceId: String): TaskItem? {
    val title = getString("title") ?: return null
    return TaskItem(
        id = id,
        spaceId = spaceId,
        title = title,
        note = getString("note") ?: "",
        sectionId = getString("sectionId"),
        dueAt = getTimestamp("dueAt")?.toMillis(),
        hasTime = getBoolean("hasTime") ?: false,
        done = getBoolean("done") ?: false,
        doneAt = getTimestamp("doneAt")?.toMillis(),
        doneByName = getString("doneByName"),
        createdByName = getString("createdByName"),
        createdAt = getTimestamp("createdAt")?.toMillis() ?: 0L,
    )
}

internal fun Timestamp.toMillis(): Long = seconds * 1000 + nanoseconds / 1_000_000
internal fun Long.toTimestamp(): Timestamp = Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())
