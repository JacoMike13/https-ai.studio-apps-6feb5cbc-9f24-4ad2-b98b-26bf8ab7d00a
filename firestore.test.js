const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_juez_123";
const ALICE_EMAIL = "alice.titular@gmail.com";
const BOB_UID = "bob_defensor_456";
const BOB_EMAIL = "bob.colega@gmail.com";
const EVE_UID = "eve_intruder_789";
const EVE_EMAIL = "eve.extra@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function buildKotlinCreatePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    sharedEmails: [],
    nuc: "NUC-2026-00142",
    numeroCausa: "CP-142/2026",
    tipoAudiencia: "Control de Detención",
    fechaHoraMillis: Date.now() + 86400000,
    salaOJuzgado: "Sala 4 - Oralidad Penal",
    juez: "Lic. Roberto Mendoza",
    ministerioPublico: "Lic. Laura Vázquez",
    asesorJuridico: "Lic. Carlos Herrera",
    victima: "María Fernanda Gómez",
    defensor: "Lic. Andrés Morales",
    imputado: "Juan Pérez López",
    tipoDelito: "Robo calificado",
    clasificacionJuridica: "Doloso, consumado instantáneamente, coautoría directa (Art. 220 CP)",
    estado: "PROGRAMADA",
    resultado: "",
    tieneProximaFecha: false,
    proximoTipoAudiencia: "",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildAgendaPayload(uid, email, sharedEmails = []) {
  const now = new Date(Date.now() - 1000);
  return {
    ownerUid: uid,
    ownerEmail: email,
    ownerName: "Titular",
    sharedEmails,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write agendas or audiencias", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("agendas").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/audiencias`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/audiencias`)
      .doc("aud_1")
      .set(buildKotlinCreatePayload(ALICE_UID))
  );
});

test("Owner can manage shared Gmail list, update hearings, and auto-schedule", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("agendas").doc(ALICE_UID).set(buildAgendaPayload(ALICE_UID, ALICE_EMAIL, [BOB_EMAIL]))
  );

  const colRef = aliceDb.collection(`users/${ALICE_UID}/audiencias`);
  await assertSucceeds(
    colRef.doc("aud_1").set(buildKotlinCreatePayload(ALICE_UID, { sharedEmails: [BOB_EMAIL] }))
  );
  await assertSucceeds(colRef.orderBy("fechaHoraMillis", "asc").get());

  // Atomic batch update + auto-schedule preserving sharedEmails
  const nextDateMillis = Date.now() + 7 * 86400000;
  const batch = aliceDb.batch();
  batch.update(colRef.doc("aud_1"), {
    estado: "CELEBRADA",
    resultado: "Se califica legal la detención.",
    tieneProximaFecha: true,
    proximaFechaMillis: nextDateMillis,
    proximoTipoAudiencia: "Vinculación a Proceso",
    audienciaSiguienteId: "aud_2",
    updatedAt: new Date(Date.now() - 500),
  });
  batch.set(
    colRef.doc("aud_2"),
    buildKotlinCreatePayload(ALICE_UID, {
      sharedEmails: [BOB_EMAIL],
      tipoAudiencia: "Vinculación a Proceso",
      fechaHoraMillis: nextDateMillis,
      audienciaOrigenId: "aud_1",
    })
  );
  await assertSucceeds(batch.commit());
});

test("Invited Gmail collaborator can read shared agenda and audiencias, but CANNOT modify or delete", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("agendas").doc(ALICE_UID).set(buildAgendaPayload(ALICE_UID, ALICE_EMAIL, [BOB_EMAIL]));
    await db
      .collection(`users/${ALICE_UID}/audiencias`)
      .doc("aud_shared")
      .set(buildKotlinCreatePayload(ALICE_UID, { sharedEmails: [BOB_EMAIL] }));
  });

  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();

  // Bob can discover agendas shared with his verified Gmail
  await assertSucceeds(
    bobDb.collection("agendas").where("sharedEmails", "array-contains", BOB_EMAIL).get()
  );
  // Bob can query and read Alice's shared audiencias
  await assertSucceeds(
    bobDb
      .collection(`users/${ALICE_UID}/audiencias`)
      .where("sharedEmails", "array-contains", BOB_EMAIL)
      .get()
  );
  await assertSucceeds(bobDb.collection(`users/${ALICE_UID}/audiencias`).doc("aud_shared").get());

  // Bob CANNOT mutate, create, or delete Alice's hearings or sharing config
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/audiencias`)
      .doc("aud_shared")
      .update({ juez: "Modificado por invitado", updatedAt: new Date(Date.now() - 200) })
  );
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/audiencias`)
      .doc("aud_new")
      .set(buildKotlinCreatePayload(ALICE_UID))
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/audiencias`).doc("aud_shared").delete());
  await assertFails(
    bobDb.collection("agendas").doc(ALICE_UID).update({ sharedEmails: [BOB_EMAIL, EVE_EMAIL] })
  );
});

test("Email spoofing & isolation: unverified email or non-invited user is rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("agendas").doc(ALICE_UID).set(buildAgendaPayload(ALICE_UID, ALICE_EMAIL, [BOB_EMAIL]));
    await db
      .collection(`users/${ALICE_UID}/audiencias`)
      .doc("aud_shared")
      .set(buildKotlinCreatePayload(ALICE_UID, { sharedEmails: [BOB_EMAIL] }));
  });

  // Spoofed unverified email matching BOB_EMAIL must be rejected
  const spoofedBobDb = testEnv
    .authenticatedContext("spoof_uid", { email: BOB_EMAIL, email_verified: false })
    .firestore();
  await assertFails(
    spoofedBobDb.collection("agendas").where("sharedEmails", "array-contains", BOB_EMAIL).get()
  );
  await assertFails(
    spoofedBobDb
      .collection(`users/${ALICE_UID}/audiencias`)
      .where("sharedEmails", "array-contains", BOB_EMAIL)
      .get()
  );

  // Eve (not in sharedEmails) must be rejected
  const eveDb = testEnv
    .authenticatedContext(EVE_UID, { email: EVE_EMAIL, email_verified: true })
    .firestore();
  await assertFails(eveDb.collection(`users/${ALICE_UID}/audiencias`).doc("aud_shared").get());
  await assertFails(eveDb.collection(`users/${ALICE_UID}/audiencias`).get());
});

test("Reject invalid payloads: shadow id, ghost field, oversized sharedEmails, immutable createdAt", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const colRef = aliceDb.collection(`users/${ALICE_UID}/audiencias`);

  await assertFails(colRef.doc("aud_bad_id").set(buildKotlinCreatePayload(ALICE_UID, { id: "aud_bad_id" })));
  await assertFails(colRef.doc("aud_ghost").set(buildKotlinCreatePayload(ALICE_UID, { isAdmin: true })));
  await assertFails(colRef.doc("aud_long_nuc").set(buildKotlinCreatePayload(ALICE_UID, { nuc: "N".repeat(81) })));

  const tooManyEmails = Array.from({ length: 26 }, (_, i) => `user${i}@gmail.com`);
  await assertFails(
    aliceDb
      .collection("agendas")
      .doc(ALICE_UID)
      .set(buildAgendaPayload(ALICE_UID, ALICE_EMAIL, tooManyEmails))
  );

  await assertSucceeds(colRef.doc("aud_imm").set(buildKotlinCreatePayload(ALICE_UID)));
  await assertFails(
    colRef.doc("aud_imm").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Owner can configure username and app titles, while collaborator and oversized titles are rejected", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("agendas").doc(ALICE_UID).set(buildAgendaPayload(ALICE_UID, ALICE_EMAIL, [BOB_EMAIL]))
  );

  await assertSucceeds(
    aliceDb.collection("agendas").doc(ALICE_UID).update({
      ownerName: "Lic. Miguel Jacobo",
      appTitle: "Agenda Especializada",
      calendarTitle: "Calendario Penal",
      directoryTitle: "Listado General",
      summaryTitle: "Métricas Mensuales",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertFails(
    aliceDb.collection("agendas").doc(ALICE_UID).update({
      appTitle: "T".repeat(121),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertFails(
    bobDb.collection("agendas").doc(ALICE_UID).update({
      ownerName: "Intruso",
      appTitle: "Hackeado",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
