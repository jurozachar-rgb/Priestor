package sk.kvz.priestor.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

/**
 * Prístup k dátam vo Firestore.
 *
 * Štruktúra:
 *  spaces/{spaceId}                 – oblasť (name, ownerId, members[], memberNames{}, sections[], color)
 *  spaces/{spaceId}/tasks/{taskId}  – úloha
 *  invites/{kód}                    – pozvánka do oblasti (spaceId, spaceName, createdBy, expiresAt)
 *
 * Zápisy nečakajú na server – Firestore ich uloží lokálne a odošle, keď je sieť.
 */
object Repo {
    private const val TAG = "Repo"
    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore
    private val spaces get() = db.collection("spaces")
    private val invites get() = db.collection("invites")

    val user: FirebaseUser? get() = auth.currentUser
    val uid: String get() = user?.uid ?: error("Nie ste prihlásený")
    val myName: String get() = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email ?: "Ja"

    fun userFlow(): Flow<FirebaseUser?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    // ---------- Oblasti ----------

    fun spacesFlow(): Flow<List<Space>> = callbackFlow {
        val reg = spaces.whereArrayContains("members", uid).addSnapshotListener { snap, e ->
            if (e != null) { Log.w(TAG, "spaces", e); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { it.toSpace() }.orEmpty()
                .sortedBy { it.name.lowercase() }
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun spaceFlow(spaceId: String): Flow<Space?> = callbackFlow {
        val reg = spaces.document(spaceId).addSnapshotListener { snap, e ->
            if (e != null) { Log.w(TAG, "space", e); trySend(null); return@addSnapshotListener }
            trySend(snap?.toSpace())
        }
        awaitClose { reg.remove() }
    }

    fun createSpace(name: String, sectionNames: List<String>, color: Int): String {
        val ref = spaces.document()
        ref.set(
            mapOf(
                "name" to name.trim(),
                "ownerId" to uid,
                "members" to listOf(uid),
                "memberNames" to mapOf(uid to myName),
                "sections" to sectionNames.map { it.trim() }.filter { it.isNotEmpty() }
                    .map { mapOf("id" to newId(), "name" to it) },
                "color" to color,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).logFailure("createSpace")
        return ref.id
    }

    fun renameSpace(spaceId: String, name: String) {
        spaces.document(spaceId).update("name", name.trim()).logFailure("renameSpace")
    }

    fun updateSections(spaceId: String, sections: List<Section>) {
        spaces.document(spaceId)
            .update("sections", sections.map { mapOf("id" to it.id, "name" to it.name.trim()) })
            .logFailure("updateSections")
    }

    /** Vlastník zmaže oblasť aj so všetkými úlohami a pozvánkami. */
    suspend fun deleteSpace(spaceId: String) {
        val ref = spaces.document(spaceId)
        val tasks = ref.collection("tasks").get().await()
        tasks.documents.chunked(400).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { b.delete(it.reference) }
            b.commit().await()
        }
        invites.whereEqualTo("createdBy", uid).whereEqualTo("spaceId", spaceId).get().await()
            .documents.forEach { it.reference.delete().await() }
        ref.delete().await()
    }

    suspend fun leaveSpace(spaceId: String) {
        spaces.document(spaceId).update(
            mapOf(
                "members" to FieldValue.arrayRemove(uid),
                "memberNames.$uid" to FieldValue.delete(),
            )
        ).await()
    }

    fun removeMember(spaceId: String, memberUid: String) {
        spaces.document(spaceId).update(
            mapOf(
                "members" to FieldValue.arrayRemove(memberUid),
                "memberNames.$memberUid" to FieldValue.delete(),
            )
        ).logFailure("removeMember")
    }

    // ---------- Pozvánky ----------

    /** Vytvorí pozvánku platnú 7 dní a vráti jej kód. Vyžaduje pripojenie. */
    suspend fun createInvite(space: Space): String {
        val code = newInviteCode()
        invites.document(code).set(
            mapOf(
                "spaceId" to space.id,
                "spaceName" to space.name,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to (System.currentTimeMillis() + 7L * 24 * 3600 * 1000).toTimestamp(),
            )
        ).await()
        return code
    }

    /** Pripojí prihláseného používateľa do oblasti podľa kódu. Vráti ID oblasti. */
    suspend fun joinByCode(rawCode: String): Result<String> = runCatching {
        val code = rawCode.trim().uppercase().filter { it.isLetterOrDigit() }
        require(code.length == INVITE_LEN) { "Kód má mať $INVITE_LEN znakov." }
        val invite = invites.document(code).get().await()
        require(invite.exists()) { "Pozvánka neexistuje." }
        val expires = invite.getTimestamp("expiresAt")
        require(expires == null || expires > Timestamp.now()) { "Platnosť pozvánky vypršala." }
        val spaceId = invite.getString("spaceId") ?: error("Neplatná pozvánka.")
        spaces.document(spaceId).update(
            mapOf(
                "members" to FieldValue.arrayUnion(uid),
                "memberNames.$uid" to myName,
                "lastJoinCode" to code,
            )
        ).await()
        spaceId
    }

    // ---------- Úlohy ----------

    fun tasksFlow(spaceId: String): Flow<List<TaskItem>> = callbackFlow {
        val reg = spaces.document(spaceId).collection("tasks").addSnapshotListener { snap, e ->
            if (e != null) { Log.w(TAG, "tasks", e); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toTask(spaceId) }.orEmpty())
        }
        awaitClose { reg.remove() }
    }

    fun saveTask(
        spaceId: String,
        taskId: String?,
        title: String,
        note: String,
        sectionId: String?,
        dueAt: Long?,
        hasTime: Boolean,
    ) {
        val col = spaces.document(spaceId).collection("tasks")
        val data = mutableMapOf<String, Any?>(
            "title" to title.trim(),
            "note" to note.trim(),
            "sectionId" to sectionId,
            "dueAt" to dueAt?.toTimestamp(),
            "hasTime" to (dueAt != null && hasTime),
        )
        if (taskId == null) {
            data += mapOf(
                "done" to false,
                "doneAt" to null,
                "doneByName" to null,
                "createdBy" to uid,
                "createdByName" to myName,
                "createdAt" to FieldValue.serverTimestamp(),
            )
            col.document().set(data).logFailure("addTask")
        } else {
            col.document(taskId).update(data).logFailure("updateTask")
        }
    }

    fun setDone(task: TaskItem, done: Boolean) {
        spaces.document(task.spaceId).collection("tasks").document(task.id).update(
            mapOf(
                "done" to done,
                "doneAt" to if (done) FieldValue.serverTimestamp() else null,
                "doneByName" to if (done) myName else null,
            )
        ).logFailure("setDone")
    }

    fun deleteTask(task: TaskItem) {
        spaces.document(task.spaceId).collection("tasks").document(task.id).delete()
            .logFailure("deleteTask")
    }

    /** Všetky nesplnené úlohy s termínom vo všetkých mojich oblastiach – pre pripomienky. */
    suspend fun openTasksWithDue(): List<Pair<Space, TaskItem>> {
        val mySpaces = spaces.whereArrayContains("members", uid).get().await()
            .documents.mapNotNull { it.toSpace() }
        return mySpaces.flatMap { space ->
            spaces.document(space.id).collection("tasks").whereEqualTo("done", false).get().await()
                .documents.mapNotNull { it.toTask(space.id) }
                .filter { it.dueAt != null }
                .map { space to it }
        }
    }

    // ---------- Účet ----------

    /** Zmaže všetky moje dáta: vlastné oblasti zmaže, z cudzích odíde. Potom zmaže účet. */
    suspend fun deleteAllMyData() {
        val mySpaces = spaces.whereArrayContains("members", uid).get().await()
            .documents.mapNotNull { it.toSpace() }
        for (s in mySpaces) {
            if (s.ownerId == uid) deleteSpace(s.id) else leaveSpace(s.id)
        }
        invites.whereEqualTo("createdBy", uid).get().await()
            .documents.forEach { it.reference.delete().await() }
        user?.delete()?.await()
    }

    // ---------- Pomocné ----------

    fun newId(): String = UUID.randomUUID().toString().take(8)

    const val INVITE_LEN = 6
    private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    private fun newInviteCode() = (1..INVITE_LEN).map { INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)] }
        .joinToString("")

    private fun <T> com.google.android.gms.tasks.Task<T>.logFailure(what: String) =
        addOnFailureListener { Log.e(TAG, "Zápis zlyhal: $what", it) }
}
