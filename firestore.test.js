const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

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

function getValidCaseData(uid) {
  return {
    userId: uid,
    riskLevel: "CRITICAL",
    headline: "Suspicious Electricity Bill Disconnection",
    summary: "Disconnection threat with APK link",
    stage: "RECEIVED",
    timestamp: Date.now(),
    createdAt: new Date(),
    rawInputSnippet: "Dear consumer electricity will be disconnected tonight",
    hasScreenshot: false,
    emergencyTriggered: false,
    verifiedObservations: ["Contains unverified link"],
    aiInferences: ["State discom phishing pattern"],
    unknowns: ["Beneficiary bank"],
    prioritizedActions: []
  };
}

test("Unauthenticated user: cannot read cases", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cases").get());
});

test("Unauthenticated user: cannot create cases", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_1").set(getValidCaseData(ALICE_UID))
  );
});

test("Authenticated user: cannot read another user's cases", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("cases").doc("case_bob").set(getValidCaseData(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("cases").doc("case_bob").get());
});

test("Authenticated user: cannot create case under another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("cases").doc("case_bob_by_alice").set(getValidCaseData(ALICE_UID))
  );
});

test("Authenticated user: cannot delete another user's case", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("cases").doc("case_bob").set(getValidCaseData(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("cases").doc("case_bob").delete());
});

test("Authenticated user: can create valid case under their own path", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_alice_1").set(getValidCaseData(ALICE_UID))
  );
});

test("Authenticated user: cannot create case with invalid risk level", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invalidData = {
    ...getValidCaseData(ALICE_UID),
    riskLevel: "EXTREME_SUPER_HIGH" // not allowed
  };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_alice_2").set(invalidData)
  );
});

test("Authenticated user: can read and delete their own case", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_alice_3").set(getValidCaseData(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_alice_3").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cases").doc("case_alice_3").delete()
  );
});
