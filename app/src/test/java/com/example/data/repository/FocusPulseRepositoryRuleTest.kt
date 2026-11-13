package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AppLimitRule
import com.example.data.model.BlockedSite
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.InventoryItem
import com.example.data.model.NoteItem
import com.example.data.model.ReorderRecord
import com.example.data.model.SupportTicket
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FocusPulseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun workspaceAndCollections_authenticatedOwner_succeedsCrudAndRealtimeObserve() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = FocusPulseRepository(firestore)

        val configResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureWorkspaceConfig("Alice")
        }
        assertTrue(configResult.isSuccess)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveWorkspaceConfig(
                configResult.getOrThrow().copy(
                    appLayoutStyle = "bento_grid",
                    navBarStyle = "floating_dock",
                    partColors = mapOf(
                        "background" to "#0B1120",
                        "surface" to "#151F32",
                        "primary" to "#10B981",
                        "text" to "#F8FAFC"
                    ),
                    backgroundPreset = "custom_photo",
                    backgroundOverlayAlpha = 0.55,
                    cardOpacity = 0.85
                )
            ).getOrThrow()
        }

        val habitId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertHabit(
                HabitItem(
                    title = "No Doomscroll Before Noon",
                    category = "focus",
                    targetPerDay = 1L
                )
            ).getOrThrow()
        }
        assertTrue(habitId.isNotEmpty())

        val habits = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHabits(uid).first { list -> list.any { it.id == habitId } }
        }
        assertTrue(habits.any { it.id == habitId && it.title == "No Doomscroll Before Noon" })

        val ruleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertAppRule(
                AppLimitRule(
                    packageName = "com.zhiliaoapp.musically",
                    appName = "TikTok",
                    dailyLimitMinutes = 25L
                )
            ).getOrThrow()
        }
        assertTrue(ruleId.isNotEmpty())

        val siteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertBlockedSite(
                BlockedSite(domain = "tiktok.com", label = "TikTok Web")
            ).getOrThrow()
        }
        assertTrue(siteId.isNotEmpty())

        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertNote(
                NoteItem(title = "Evening Shutdown", content = "Lock phone at 10 PM", category = "reminder")
            ).getOrThrow()
        }
        assertTrue(noteId.isNotEmpty())

        val itemId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertInventoryItem(
                InventoryItem(
                    sku = "SKU-${UUID.randomUUID().toString().take(6)}",
                    name = "Arabica Roast 1kg",
                    stockQty = 3L,
                    lowStockThreshold = 5L,
                    reorderQty = 20L,
                    unitCost = 14.0,
                    unitPrice = 32.0
                )
            ).getOrThrow()
        }
        assertTrue(itemId.isNotEmpty())

        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createReorder(
                ReorderRecord(
                    itemId = itemId,
                    itemName = "Arabica Roast 1kg",
                    sku = "SKU-1",
                    quantity = 20L,
                    totalCost = 280.0,
                    status = "ordered",
                    isAutoTriggered = true
                )
            ).getOrThrow()
        }
        assertTrue(orderId.isNotEmpty())

        val diaryId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveDiaryEntry(
                DiaryEntry(
                    dateKey = "2026-11-12",
                    activitiesText = "Finished inventory audit and deep work sprint.",
                    moodScore = 4L,
                    energyScore = 5L,
                    onlineHours = 2.5
                )
            ).getOrThrow()
        }
        assertTrue(diaryId.isNotEmpty())

        val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logFocusSession(
                FocusSession(
                    title = "Deep Work Block",
                    durationMinutes = 45L,
                    mode = "deep_work",
                    completed = true
                )
            ).getOrThrow()
        }
        assertTrue(sessionId.isNotEmpty())
    }

    @Test
    fun observeHabits_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FocusPulseRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertHabit(
                HabitItem(title = "Alice Habit", category = "wellness", targetPerDay = 1L)
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FocusPulseRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeHabits(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user access")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun observeHabits_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = FocusPulseRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeHabits(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
        }
    }

    @Test
    fun supportTicket_createAndObserveOwnTickets_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FocusPulseRepository(firestore)

        val ticketId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createSupportTicket(
                SupportTicket(
                    userEmail = ALICE_EMAIL,
                    category = "ai",
                    message = "Need assistance with AI diary feedback",
                    aiDiagnosis = "Escalated to Admin console.",
                    status = "escalated"
                )
            ).getOrThrow()
        }
        assertTrue(ticketId.isNotEmpty())

        val tickets = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMySupportTickets(aliceUid).first { list -> list.any { it.id == ticketId } }
        }
        assertTrue(tickets.any { it.id == ticketId && it.category == "ai" })
    }

    private companion object {
        const val ALICE_EMAIL = "alice_fp@test.com"
        const val BOB_EMAIL = "bob_fp@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
