package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppLimitRule
import com.example.data.model.BlockedSite
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.InventoryItem
import com.example.data.model.NoteItem
import com.example.data.model.ReorderRecord
import com.example.data.model.UserWorkspaceConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FocusPulseRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100)

    private fun newSafeId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- Real-time Observers ---

    fun observeWorkspaceConfig(uid: String): Flow<UserWorkspaceConfig?> =
        userDoc(uid)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserWorkspaceConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error -> logAndRethrow(error, OperationType.GET, "users/$uid") }

    fun observeAppRules(uid: String): Flow<List<AppLimitRule>> =
        observeSubcollection(uid, "appRules", AppLimitRule::class.java)

    fun observeBlockedSites(uid: String): Flow<List<BlockedSite>> =
        observeSubcollection(uid, "blockedSites", BlockedSite::class.java)

    fun observeHabits(uid: String): Flow<List<HabitItem>> =
        observeSubcollection(uid, "habits", HabitItem::class.java)

    fun observeNotes(uid: String): Flow<List<NoteItem>> =
        observeSubcollection(uid, "notes", NoteItem::class.java)

    fun observeInventory(uid: String): Flow<List<InventoryItem>> =
        observeSubcollection(uid, "inventory", InventoryItem::class.java)

    fun observeReorders(uid: String): Flow<List<ReorderRecord>> =
        observeSubcollection(uid, "reorders", ReorderRecord::class.java)

    fun observeDiaryEntries(uid: String): Flow<List<DiaryEntry>> =
        observeSubcollection(uid, "diary", DiaryEntry::class.java)

    fun observeFocusSessions(uid: String): Flow<List<FocusSession>> =
        observeSubcollection(uid, "focusSessions", FocusSession::class.java)

    private fun <T : Any> observeSubcollection(
        uid: String,
        subcollection: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val path = "users/$uid/$subcollection"
        emitAll(
            userDoc(uid).collection(subcollection)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        clazz,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logAndRethrow(error, OperationType.LIST, path)
                }
        )
    }

    private fun logAndRethrow(error: Throwable, op: OperationType, path: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // --- Workspace Config ---

    suspend fun ensureWorkspaceConfig(displayName: String): Result<UserWorkspaceConfig> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        val existingSnap = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        if (existingSnap.exists()) {
            existingSnap.toObject(
                UserWorkspaceConfig::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserWorkspaceConfig(userId = uid, displayName = displayName)
        } else {
            val initial = UserWorkspaceConfig(
                userId = uid,
                displayName = displayName.ifBlank { "Master Mind User" },
                lastActiveDevice = deviceLabel()
            )
            try {
                docRef.set(initial.toCreateMap(uid, deviceLabel())).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
            initial
        }
    }

    suspend fun saveWorkspaceConfig(config: UserWorkspaceConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(config.toUpdateMap(deviceLabel())).await()
            } else {
                docRef.set(config.toCreateMap(uid, deviceLabel())).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- App Limit Rules ---

    suspend fun upsertAppRule(rule: AppLimitRule): Result<String> = runCatching {
        val uid = requireUserId()
        val id = rule.id.ifBlank { newSafeId("rule") }
        val docRef = userDoc(uid).collection("appRules").document(id)
        try {
            if (rule.id.isBlank()) {
                docRef.set(rule.toCreateMap(uid)).await()
            } else {
                docRef.update(rule.toUpdateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (rule.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
        id
    }

    suspend fun deleteAppRule(ruleId: String): Result<Unit> = deleteSubdoc("appRules", ruleId)

    // --- Blocked Sites ---

    suspend fun upsertBlockedSite(site: BlockedSite): Result<String> = runCatching {
        val uid = requireUserId()
        val id = site.id.ifBlank { newSafeId("site") }
        val docRef = userDoc(uid).collection("blockedSites").document(id)
        try {
            if (site.id.isBlank()) {
                docRef.set(site.toCreateMap(uid)).await()
            } else {
                docRef.update(site.toUpdateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (site.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
        id
    }

    suspend fun deleteBlockedSite(siteId: String): Result<Unit> = deleteSubdoc("blockedSites", siteId)

    // --- Habits ---

    suspend fun upsertHabit(habit: HabitItem): Result<String> = runCatching {
        val uid = requireUserId()
        val id = habit.id.ifBlank { newSafeId("hab") }
        val docRef = userDoc(uid).collection("habits").document(id)
        try {
            if (habit.id.isBlank()) {
                docRef.set(habit.toCreateMap(uid)).await()
            } else {
                docRef.update(habit.toUpdateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (habit.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
        id
    }

    suspend fun deleteHabit(habitId: String): Result<Unit> = deleteSubdoc("habits", habitId)

    // --- Notes & Reminders ---

    suspend fun upsertNote(note: NoteItem): Result<String> = runCatching {
        val uid = requireUserId()
        val id = note.id.ifBlank { newSafeId("note") }
        val docRef = userDoc(uid).collection("notes").document(id)
        try {
            if (note.id.isBlank()) {
                docRef.set(note.toCreateMap(uid)).await()
            } else {
                docRef.update(note.toUpdateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (note.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
        id
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = deleteSubdoc("notes", noteId)

    // --- Inventory & Auto-Reordering ---

    suspend fun upsertInventoryItem(item: InventoryItem): Result<String> = runCatching {
        val uid = requireUserId()
        val id = item.id.ifBlank { newSafeId("inv") }
        val docRef = userDoc(uid).collection("inventory").document(id)
        try {
            if (item.id.isBlank()) {
                docRef.set(item.toCreateMap(uid)).await()
            } else {
                docRef.update(item.toUpdateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (item.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
        id
    }

    suspend fun deleteInventoryItem(itemId: String): Result<Unit> = deleteSubdoc("inventory", itemId)

    suspend fun createReorder(record: ReorderRecord): Result<String> = runCatching {
        val uid = requireUserId()
        val id = record.id.ifBlank { newSafeId("ord") }
        val docRef = userDoc(uid).collection("reorders").document(id)
        try {
            docRef.set(record.toCreateMap(uid)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        id
    }

    suspend fun markReorderReceived(reorder: ReorderRecord, currentItem: InventoryItem?): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            require(reorder.id.isNotBlank()) { "Reorder ID must not be blank" }
            val reorderRef = userDoc(uid).collection("reorders").document(reorder.id)
            try {
                reorderRef.update(
                    mapOf(
                        "status" to "received",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                if (currentItem != null && currentItem.id.isNotBlank()) {
                    val updatedItem = currentItem.copy(
                        stockQty = currentItem.stockQty + reorder.quantity
                    )
                    userDoc(uid).collection("inventory").document(currentItem.id)
                        .update(updatedItem.toUpdateMap()).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, reorderRef.path)
                throw e
            }
        }

    // --- Diary Entries ---

    suspend fun saveDiaryEntry(entry: DiaryEntry): Result<String> = runCatching {
        val uid = requireUserId()
        val safeDateId = "diary_${entry.dateKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}"
        val id = entry.id.ifBlank { safeDateId }
        val docRef = userDoc(uid).collection("diary").document(id)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(entry.toUpdateMap()).await()
            } else {
                docRef.set(entry.toCreateMap(uid)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
        id
    }

    // --- Focus Sessions ---

    suspend fun logFocusSession(session: FocusSession): Result<String> = runCatching {
        val uid = requireUserId()
        val id = session.id.ifBlank { newSafeId("sess") }
        val docRef = userDoc(uid).collection("focusSessions").document(id)
        try {
            docRef.set(session.toCreateMap(uid)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        id
    }

    private suspend fun deleteSubdoc(collection: String, docId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(docId.isNotBlank()) { "Document ID must not be blank" }
        val docRef = userDoc(uid).collection(collection).document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
