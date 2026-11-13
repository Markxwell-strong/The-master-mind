const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverNow = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write any FocusPulse collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("habits").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("inventory").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("diary").get());
});

test("Authenticated owner: can create and update UserWorkspaceConfig with per-part colors and layouts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      displayName: "Alice",
      activeDashboardId: "command_center",
      themePalette: "emerald",
      darkModePreference: "dark",
      cardStyle: "elevated",
      cornerRadiusDp: 16,
      compactDensity: false,
      appLayoutStyle: "bento_grid",
      navBarStyle: "floating_dock",
      partColors: {
        background: "#0B1120",
        surface: "#151F32",
        primary: "#10B981",
        secondary: "#38BDF8",
        tertiary: "#F59E0B",
        text: "#F8FAFC",
        mutedText: "#94A3B8",
        navBar: "#0F172A",
        border: "#334155",
        buttonText: "#FFFFFF",
      },
      backgroundPreset: "custom_photo",
      backgroundOverlayAlpha: 0.55,
      cardOpacity: 0.85,
      enabledWidgets: ["focus_score", "screen_time"],
      bedtimeHour: 22,
      bedtimeMinute: 30,
      wakeHour: 6,
      wakeMinute: 30,
      sleepLockEnabled: true,
      deepWorkMinutes: 50,
      deepWorkDndEnabled: true,
      deepWorkActiveUntilMs: 0,
      inactivityAlertHours: 3,
      inactivityFullScreenAlarm: false,
      aiCoachPersona: "balanced",
      lastActiveDevice: "Pixel 9",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    ref.update({
      activeDashboardId: "anti_doomscroll",
      themePalette: "cobalt",
      appLayoutStyle: "split_carousel",
      partColors: { background: "#140B29" },
      updatedAt: serverNow(),
    })
  );

  await assertFails(
    ref.update({
      partColors: { invalidPartKey: "#FFFFFF" },
      updatedAt: serverNow(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's workspace, habits, notes, inventory, or diary", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      activeDashboardId: "command_center",
      themePalette: "emerald",
      darkModePreference: "dark",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await db.collection("users").doc(ALICE_UID).collection("habits").doc("hab_1").set({
      userId: ALICE_UID,
      title: "Morning Walk",
      category: "wellness",
      targetPerDay: 1,
      completedToday: 0,
      streakDays: 3,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("habits").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("habits").doc("hab_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Hack",
      content: "No",
      category: "note",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );
});

test("Shadow update & identity spoofing: rejects ghost fields and mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("hab_ghost").set({
      userId: ALICE_UID,
      title: "Deep Work",
      category: "focus",
      targetPerDay: 1,
      isAdmin: true,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("appRules").doc("rule_spoof").set({
      userId: BOB_UID,
      packageName: "com.zhiliaoapp.musically",
      appName: "TikTok",
      dailyLimitMinutes: 30,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );
});

test("Subcollection CRUD & Reorder terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const base = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    base.collection("appRules").doc("rule_1").set({
      userId: ALICE_UID,
      packageName: "com.zhiliaoapp.musically",
      appName: "TikTok",
      category: "social",
      dailyLimitMinutes: 20,
      todayUsedMinutes: 15,
      blockDuringSleep: true,
      blockDuringDeepWork: true,
      isLocked: false,
      lockedUntilMillis: 0,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    base.collection("blockedSites").doc("site_1").set({
      userId: ALICE_UID,
      domain: "tiktok.com",
      label: "TikTok Web",
      blockDuringDeepWork: true,
      blockAlways: false,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    base.collection("inventory").doc("inv_1").set({
      userId: ALICE_UID,
      sku: "SKU-101",
      name: "Matcha Tin",
      category: "Beverage",
      stockQty: 4,
      lowStockThreshold: 10,
      reorderQty: 25,
      autoReorder: true,
      unitCost: 12.5,
      unitPrice: 28.0,
      unitsSold: 42,
      supplierName: "Kyoto Supply",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  const reorderRef = base.collection("reorders").doc("ord_1");
  await assertSucceeds(
    reorderRef.set({
      userId: ALICE_UID,
      itemId: "inv_1",
      itemName: "Matcha Tin",
      sku: "SKU-101",
      quantity: 25,
      totalCost: 312.5,
      supplierName: "Kyoto Supply",
      status: "ordered",
      isAutoTriggered: true,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    reorderRef.update({
      status: "received",
      updatedAt: serverNow(),
    })
  );

  // Terminal state: once status == 'received', further updates are blocked
  await assertFails(
    reorderRef.update({
      status: "pending",
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    base.collection("diary").doc("diary_2026_11_12").set({
      userId: ALICE_UID,
      dateKey: "2026-11-12",
      activitiesText: "Shipped inventory audit and completed 90m deep work.",
      moodScore: 4,
      energyScore: 4,
      onlineHours: 3.5,
      voiceTranscribed: true,
      aiScore: 88,
      aiFeedback: "Great deep work consistency.",
      aiRecommendations: ["Take a 15m walk before dinner"],
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    base.collection("focusSessions").doc("sess_1").set({
      userId: ALICE_UID,
      title: "Morning Sprint",
      durationMinutes: 50,
      mode: "deep_work",
      completed: true,
      distractionsBlocked: 4,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );
});

test("Admin privileges (cleandemon5@gmail.com) & SupportTicket isolation", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext("admin_uid_1", { email: "cleandemon5@gmail.com", email_verified: true }).firestore();

  // Regular user cannot self-promote to admin
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      role: "admin",
      activeDashboardId: "command_center",
      themePalette: "emerald",
      darkModePreference: "dark",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Regular user creates normal member workspace & support ticket
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      role: "member",
      activeDashboardId: "command_center",
      themePalette: "emerald",
      darkModePreference: "dark",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    bobDb.collection("supportTickets").doc("tkt_1").set({
      userId: BOB_UID,
      userEmail: "bob@example.com",
      category: "ai",
      message: "Need help with AI diary evaluation",
      status: "escalated",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Removed legacy category and username field are rejected
  await assertFails(
    bobDb.collection("supportTickets").doc("tkt_legacy").set({
      userId: BOB_UID,
      userEmail: "bob@example.com",
      category: "forgot_password",
      message: "Legacy test",
      status: "open",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Alice cannot read Bob's support ticket or list users
  await assertFails(aliceDb.collection("supportTickets").doc("tkt_1").get());
  await assertFails(aliceDb.collection("supportTickets").get());
  await assertFails(aliceDb.collection("users").get());

  // Bob can query only his own support tickets
  await assertSucceeds(bobDb.collection("supportTickets").where("userId", "==", BOB_UID).get());

  // Admin (cleandemon5@gmail.com verified) can list users, list tickets, and reply
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(adminDb.collection("supportTickets").get());
  await assertSucceeds(
    adminDb.collection("supportTickets").doc("tkt_1").update({
      status: "resolved",
      adminReply: "AI Logic updated and verified.",
      updatedAt: serverNow(),
    })
  );

  // Admin cannot arbitrarily modify another user's workspace doc
  await assertFails(
    adminDb.collection("users").doc(BOB_UID).update({
      displayName: "Hijacked",
      updatedAt: serverNow(),
    })
  );

  // Main Admin can promote Bob to delegated admin (updating ONLY role & updatedAt)
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      role: "admin",
      updatedAt: serverNow(),
    })
  );

  // Main Admin can assign support ticket to Bob via sharedAdminUids
  await assertSucceeds(
    adminDb.collection("supportTickets").doc("tkt_1").update({
      sharedAdminUids: [BOB_UID],
      updatedAt: serverNow(),
    })
  );

  // Promoted sub-admin Bob CAN query & reply to tickets assigned to him,
  // but STILL CANNOT list /users (only Main Admin sees all users)
  await assertFails(bobDb.collection("users").get());
  await assertSucceeds(
    bobDb.collection("supportTickets").where("sharedAdminUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("supportTickets").doc("tkt_1").update({
      adminReply: "Handled by support team.",
      status: "resolved",
      updatedAt: serverNow(),
    })
  );
});

test("Business Accounting & Finances: owner-only access, immutable ledger updates, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const base = aliceDb.collection("users").doc(ALICE_UID);

  // Owner initializes business account
  const accRef = base.collection("businessAccount").doc("default");
  await assertSucceeds(
    accRef.set({
      userId: ALICE_UID,
      businessName: "Nairobi Mart",
      isInitialized: true,
      openingCash: 50000,
      currentCash: 50000,
      totalCapitalAdded: 0,
      startedAtMillis: Date.now(),
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Owner records immutable financial ledger entry
  const ledgerRef = base.collection("businessLedger").doc("led_1");
  await assertSucceeds(
    ledgerRef.set({
      userId: ALICE_UID,
      entryType: "expense",
      title: "Delivery Fuel",
      category: "Transport",
      amount: 1200,
      balanceAfter: 48800,
      relatedItemId: "",
      occurredAtMillis: Date.now(),
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Ledger entries cannot be altered (immutable audit trail)
  await assertFails(
    ledgerRef.update({
      amount: 100,
      updatedAt: serverNow(),
    })
  );

  // Owner creates and settles debtor record
  const debtRef = base.collection("businessDebts").doc("debt_1");
  await assertSucceeds(
    debtRef.set({
      userId: ALICE_UID,
      personName: "Kamau Traders",
      phone: "0700000000",
      debtType: "debtor",
      totalAmount: 15000,
      settledAmount: 0,
      note: "2 sacks rice on credit",
      isSettled: false,
      dueAtMillis: 0,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Cross-user (even Bob) cannot read or write Alice's business account, ledger, or debts
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businessAccount").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businessLedger").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businessDebts").get());
});

