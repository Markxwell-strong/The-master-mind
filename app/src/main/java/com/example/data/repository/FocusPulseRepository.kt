package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppLimitRule
import com.example.data.model.BlockedSite
import com.example.data.model.BusinessAccountConfig
import com.example.data.model.BusinessDebtRecord
import com.example.data.model.BusinessLedgerEntry
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.InventoryItem
import com.example.data.model.NoteItem
import com.example.data.model.ReorderRecord
import com.example.data.model.SupportTicket
import com.example.data.model.UserWorkspaceConfig
import com.example.system.AccountSecurityHelper
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

    fun observeBusinessAccount(uid: String): Flow<BusinessAccountConfig?> =
        userDoc(uid).collection("businessAccount").document("default")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        BusinessAccountConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error -> logAndRethrow(error, OperationType.GET, "users/$uid/businessAccount/default") }

    fun observeBusinessLedger(uid: String): Flow<List<BusinessLedgerEntry>> =
        observeSubcollection(uid, "businessLedger", BusinessLedgerEntry::class.java)
            .map { list -> list.sortedByDescending { it.occurredAtMillis } }

    fun observeBusinessDebts(uid: String): Flow<List<BusinessDebtRecord>> =
        observeSubcollection(uid, "businessDebts", BusinessDebtRecord::class.java)
            .map { list ->
                list.sortedWith(
                    compareBy<BusinessDebtRecord> { it.isSettled }
                        .thenByDescending { it.remainingAmount }
                )
            }

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
        val authUser = auth.currentUser
        val authEmail = authUser?.email.orEmpty().trim().lowercase()
        val shouldBeAdmin = AccountSecurityHelper.isAdminEmail(authEmail) &&
            (authUser?.isEmailVerified != false)
        val expectedRole = if (shouldBeAdmin) "admin" else "member"
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
            val loaded = existingSnap.toObject(
                UserWorkspaceConfig::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserWorkspaceConfig(userId = uid, displayName = displayName)

            val needsSync = (shouldBeAdmin && loaded.role != "admin") ||
                (authEmail.isNotBlank() && loaded.email != authEmail)
            if (needsSync) {
                val synced = loaded.copy(
                    role = if (shouldBeAdmin) "admin" else loaded.role,
                    email = if (authEmail.isNotBlank()) authEmail else loaded.email
                )
                try {
                    docRef.update(synced.toUpdateMap(deviceLabel())).await()
                } catch (_: Exception) {
                    // Best-effort role/email sync
                }
                synced
            } else {
                loaded
            }
        } else {
            val initial = UserWorkspaceConfig(
                userId = uid,
                displayName = displayName.ifBlank { "Master Mind User" },
                email = authEmail,
                role = expectedRole,
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
        val authUser = auth.currentUser
        val authEmail = authUser?.email.orEmpty().trim().lowercase()
        val isVerifiedMainAdmin = AccountSecurityHelper.isMainAdminEmail(authEmail) &&
            (authUser?.isEmailVerified != false)
        val docRef = userDoc(uid)
        try {
            val snap = docRef.get().await()
            val serverRole = snap.getString("role") ?: "member"
            val effectiveRole = when {
                isVerifiedMainAdmin -> "admin"
                snap.exists() && serverRole == "admin" -> "admin"
                else -> "member"
            }
            val safeConfig = config.copy(
                role = effectiveRole,
                email = if (authEmail.isNotBlank()) authEmail else config.email
            )
            if (snap.exists()) {
                docRef.update(safeConfig.toUpdateMap(deviceLabel())).await()
            } else {
                docRef.set(safeConfig.toCreateMap(uid, deviceLabel())).await()
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

    // --- Business Accounting (Cash, Capital, Restock Deductions, Expenses, Debtors & Creditors) ---

    suspend fun initializeBusinessAccount(
        businessName: String,
        openingCash: Double
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanName = businessName.trim().ifBlank { "My Business" }.take(120)
        val safeCash = openingCash.coerceIn(-1000000000.0, 1000000000.0)
        val now = System.currentTimeMillis()
        val accountRef = userDoc(uid).collection("businessAccount").document("default")
        val ledgerId = newSafeId("ldg")
        val ledgerRef = userDoc(uid).collection("businessLedger").document(ledgerId)
        try {
            val existing = accountRef.get().await()
            val config = BusinessAccountConfig(
                id = "default",
                userId = uid,
                businessName = cleanName,
                isInitialized = true,
                openingCash = safeCash,
                currentCash = safeCash,
                totalCapitalAdded = 0.0,
                startedAtMillis = now
            )
            if (existing.exists()) {
                accountRef.update(config.toUpdateMap()).await()
            } else {
                accountRef.set(config.toCreateMap(uid)).await()
            }
            val entry = BusinessLedgerEntry(
                id = ledgerId,
                userId = uid,
                entryType = "opening_balance",
                title = "Opening Cash Balance ($cleanName)",
                category = "Capital",
                amount = safeCash.coerceAtLeast(0.0),
                balanceAfter = safeCash,
                occurredAtMillis = now
            )
            ledgerRef.set(entry.toCreateMap(uid)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, accountRef.path)
            throw e
        }
    }

    suspend fun recordCashMovement(
        entryType: String,
        title: String,
        category: String,
        signedDeltaCash: Double,
        capitalAdditionAmount: Double = 0.0,
        relatedItemId: String = ""
    ): Result<Double> = runCatching {
        val uid = requireUserId()
        val accountRef = userDoc(uid).collection("businessAccount").document("default")
        val ledgerId = newSafeId("ldg")
        val ledgerRef = userDoc(uid).collection("businessLedger").document(ledgerId)
        val now = System.currentTimeMillis()
        try {
            val snap = accountRef.get().await()
            val current = if (snap.exists()) {
                snap.toObject(BusinessAccountConfig::class.java) ?: BusinessAccountConfig(userId = uid)
            } else {
                BusinessAccountConfig(
                    id = "default",
                    userId = uid,
                    businessName = "My Business",
                    isInitialized = true,
                    openingCash = 0.0,
                    currentCash = 0.0,
                    totalCapitalAdded = 0.0,
                    startedAtMillis = now
                )
            }
            val newCash = current.currentCash + signedDeltaCash
            val newCapitalAdded = current.totalCapitalAdded + capitalAdditionAmount.coerceAtLeast(0.0)
            val updatedAccount = current.copy(
                userId = uid,
                businessName = current.businessName.ifBlank { "My Business" },
                isInitialized = true,
                currentCash = newCash,
                totalCapitalAdded = newCapitalAdded,
                startedAtMillis = if (current.startedAtMillis > 0L) current.startedAtMillis else now
            )
            if (snap.exists()) {
                accountRef.update(updatedAccount.toUpdateMap()).await()
            } else {
                accountRef.set(updatedAccount.toCreateMap(uid)).await()
            }
            val ledgerEntry = BusinessLedgerEntry(
                id = ledgerId,
                userId = uid,
                entryType = entryType,
                title = title.trim().ifBlank { entryType }.take(140),
                category = category.trim().ifBlank { "General" }.take(60),
                amount = kotlin.math.abs(signedDeltaCash),
                balanceAfter = newCash,
                relatedItemId = relatedItemId,
                occurredAtMillis = now
            )
            ledgerRef.set(ledgerEntry.toCreateMap(uid)).await()
            newCash
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ledgerRef.path)
            throw e
        }
    }

    suspend fun upsertDebtRecord(record: BusinessDebtRecord): Result<String> = runCatching {
        val uid = requireUserId()
        val id = record.id.ifBlank { newSafeId("dbt") }
        val docRef = userDoc(uid).collection("businessDebts").document(id)
        try {
            if (record.id.isBlank()) {
                docRef.set(record.toCreateMap(uid)).await()
            } else {
                docRef.update(record.toUpdateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (record.id.isBlank()) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            throw e
        }
        id
    }

    suspend fun deleteDebtRecord(debtId: String): Result<Unit> = deleteSubdoc("businessDebts", debtId)

    suspend fun deleteLedgerEntry(entryId: String): Result<Unit> = deleteSubdoc("businessLedger", entryId)

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

    // --- Support Tickets & Admin Password Recovery ---

    fun observeMySupportTickets(uid: String): Flow<List<SupportTicket>> = flow {
        val path = "supportTickets"
        emitAll(
            db.collection("supportTickets")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path) }
        )
    }

    fun observeAssignedSupportTicketsForSubAdmin(adminUid: String): Flow<List<SupportTicket>> = flow {
        val path = "supportTickets"
        emitAll(
            db.collection("supportTickets")
                .whereArrayContains("sharedAdminUids", adminUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path) }
        )
    }

    fun observeAllSupportTicketsForAdmin(): Flow<List<SupportTicket>> = flow {
        val path = "supportTickets"
        emitAll(
            db.collection("supportTickets")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path) }
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<UserWorkspaceConfig>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserWorkspaceConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> logAndRethrow(error, OperationType.LIST, path) }
        )
    }

    suspend fun updateUserRoleByMainAdmin(targetUid: String, role: String): Result<Unit> = runCatching {
        requireUserId()
        require(targetUid.isNotBlank()) { "Target user ID required" }
        val normalizedRole = if (role == "admin") "admin" else "member"
        val docRef = userDoc(targetUid)
        try {
            docRef.update(
                mapOf(
                    "role" to normalizedRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun assignSupportTicketAdmins(
        ticketId: String,
        sharedAdminUids: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        require(ticketId.isNotBlank()) { "Ticket ID required" }
        val docRef = db.collection("supportTickets").document(ticketId)
        try {
            docRef.update(
                mapOf(
                    "sharedAdminUids" to sharedAdminUids.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun createSupportTicket(ticket: SupportTicket): Result<String> = runCatching {
        val uid = requireUserId()
        val id = ticket.id.ifBlank { newSafeId("tkt") }
        val docRef = db.collection("supportTickets").document(id)
        try {
            docRef.set(ticket.toCreateMap(uid)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        id
    }

    suspend fun updateSupportTicketByUser(
        ticketId: String,
        status: String,
        sentToWhatsApp: Boolean,
        aiDiagnosis: String
    ): Result<Unit> = runCatching {
        requireUserId()
        require(ticketId.isNotBlank()) { "Ticket ID required" }
        val docRef = db.collection("supportTickets").document(ticketId)
        try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "sentToWhatsApp" to sentToWhatsApp,
                    "aiDiagnosis" to aiDiagnosis.take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateSupportTicketByAdmin(
        ticketId: String,
        status: String,
        adminReply: String
    ): Result<Unit> = runCatching {
        requireUserId()
        require(ticketId.isNotBlank()) { "Ticket ID required" }
        val docRef = db.collection("supportTickets").document(ticketId)
        try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "adminReply" to adminReply.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}
