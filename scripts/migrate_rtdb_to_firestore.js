/**
 * Secure Migration Script: Firebase Realtime Database -> Cloud Firestore
 * Project: hostel-buddy-ar12
 * Package: com.example.hostelbuddy
 *
 * This script safely migrates existing user roles and profiles from Realtime Database
 * (`users/{uid}`) to Cloud Firestore (`users/{uid}`), ensuring:
 * 1. ZERO data loss: Realtime Database records are strictly read-only and never deleted.
 * 2. UID consistency: Uses the exact same Firebase Auth UID as the Firestore Document ID.
 * 3. Role fidelity: Normalizes and preserves roles: student, mess_admin, laundry_admin, food_truck_admin.
 * 4. Idempotency: Running multiple times will NOT overwrite existing valid Firestore profiles unless --force is specified.
 * 5. Dry-run support: Run with --dry-run to preview what will be migrated without making any changes.
 *
 * Usage:
 *   node scripts/migrate_rtdb_to_firestore.js [--dry-run] [--force] [--key=path/to/serviceAccountKey.json]
 */

const fs = require('fs');
const path = require('path');

// Parse CLI arguments
const args = {};
process.argv.slice(2).forEach(arg => {
  if (arg.startsWith('--')) {
    const parts = arg.substring(2).split('=');
    const key = parts[0];
    const val = parts.length > 1 ? parts.slice(1).join('=') : true;
    args[key] = val;
  }
});

const isDryRun = !!args['dry-run'];
const isForce = !!args['force'];
const keyPath = args.key || process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'serviceAccountKey.json');

const RTDB_URL = 'https://hostel-buddy-ar12-default-rtdb.asia-southeast1.firebasedatabase.app';

console.log('======================================================================');
console.log('  Hostel Buddy: Realtime Database to Cloud Firestore User Migration   ');
console.log('======================================================================\n');

if (isDryRun) {
  console.log('>>> [MODE: DRY RUN] No changes will be written to Cloud Firestore. <<<\n');
}

// Normalize role matching the exact Android LoginActivity logic
function normalizeRole(role) {
  if (!role || typeof role !== 'string') return '';
  const value = role.trim().toLowerCase()
    .replace(/&/g, 'and')
    .replace(/ /g, '_')
    .replace(/-/g, '_');

  if (value === 'mess_admin' || value === 'mess_and_campus_cravings_admin') return 'mess_admin';
  if (value === 'laundry_admin') return 'laundry_admin';
  if (value === 'food_truck_admin' || value === 'campus_cravings_admin') return 'food_truck_admin';
  if (value === 'student') return 'student';
  return value;
}

// Verify service account key existence
if (!fs.existsSync(keyPath)) {
  console.warn(`[WARNING] Service account key not found at: ${keyPath}\n`);
  console.warn('To run this automated migration script:');
  console.warn('  1. Open Firebase Console: https://console.firebase.google.com/project/hostel-buddy-ar12/settings/serviceaccounts/adminsdk');
  console.warn('  2. Click "Generate new private key".');
  console.warn('  3. Save the downloaded JSON file as: scripts/serviceAccountKey.json');
  console.warn('  4. Re-run: node scripts/migrate_rtdb_to_firestore.js [--dry-run]\n');
  console.warn('----------------------------------------------------------------------');
  console.warn('Alternatively, for individual accounts you can view the required Firestore mapping:');
  console.warn('  Firestore Collection: users');
  console.warn('  Document ID:          <Firebase Auth UID>');
  console.warn('  Fields:');
  console.warn('    - uid:       "<Firebase Auth UID>"');
  console.warn('    - email:     "<User Email>"');
  console.warn('    - fullName:  "<Full Name>"');
  console.warn('    - role:      "student" | "mess_admin" | "laundry_admin" | "food_truck_admin"');
  console.warn('----------------------------------------------------------------------\n');
  process.exit(1);
}

let admin;
try {
  admin = require('firebase-admin');
} catch (e) {
  console.error('Error: firebase-admin package is not installed. Run: npm install firebase-admin --prefix scripts\n');
  process.exit(1);
}

const serviceAccount = require(path.resolve(keyPath));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: RTDB_URL,
  projectId: 'hostel-buddy-ar12'
});

const auth = admin.auth();
const rtdb = admin.database();
const firestore = admin.firestore();

async function runMigration() {
  console.log(`Connecting to Realtime Database: ${RTDB_URL}`);
  console.log('Scanning path: /users ...\n');

  try {
    const snapshot = await rtdb.ref('users').once('value');

    if (!snapshot.exists()) {
      console.log('[INFO] No user records found in Realtime Database under /users.');
      process.exit(0);
    }

    const usersData = snapshot.val();
    const uids = Object.keys(usersData);
    console.log(`Found ${uids.length} user record(s) in Realtime Database.\n`);

    let stats = {
      scanned: uids.length,
      alreadyInFirestore: 0,
      migrated: 0,
      errors: 0
    };

    for (const uid of uids) {
      const record = usersData[uid];
      let rawRole = '';

      if (typeof record === 'string') {
        rawRole = record;
      } else if (record && typeof record === 'object') {
        rawRole = record.role || '';
      }

      const role = normalizeRole(rawRole);
      if (!role) {
        console.warn(`[WARN] User ${uid} has no recognizable role (raw: "${rawRole}"). Skipping.`);
        stats.errors++;
        continue;
      }

      // Check if user already exists in Firestore
      const docRef = firestore.collection('users').document(uid);
      const existingDoc = await docRef.get();

      if (existingDoc.exists && !isForce) {
        const existingData = existingDoc.data();
        console.log(`[EXISTS] UID: ${uid} | Existing Role: "${existingData.role || 'none'}" | RTDB Role: "${role}" (skipped, use --force to overwrite)`);
        stats.alreadyInFirestore++;
        continue;
      }

      // Resolve user details from record or Firebase Auth
      let email = (record && record.email) ? record.email : '';
      let fullName = (record && (record.fullName || record.name)) ? (record.fullName || record.name) : '';

      try {
        const authUser = await auth.getUser(uid);
        if (!email && authUser.email) email = authUser.email;
        if (!fullName && authUser.displayName) fullName = authUser.displayName;
      } catch (authErr) {
        // User may exist in RTDB without Auth record or with custom token
        if (!email) email = `user_${uid.substring(0, 6)}@campus.nmims.edu`;
      }

      if (!fullName) {
        fullName = email ? email.split('@')[0] : 'Campus User';
      }

      const firestoreProfile = {
        uid: uid,
        email: email,
        fullName: fullName,
        role: role,
        migratedFrom: 'realtime_database',
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      };

      if (!existingDoc.exists) {
        firestoreProfile.createdAt = admin.firestore.FieldValue.serverTimestamp();
      }

      if (record && typeof record === 'object') {
        if (record.rollNumber) firestoreProfile.rollNumber = record.rollNumber;
        if (record.roomNumber) firestoreProfile.roomNumber = record.roomNumber;
        if (record.phone) firestoreProfile.phone = record.phone;
      }

      if (isDryRun) {
        console.log(`[WOULD MIGRATE] UID: ${uid}`);
        console.log(`                 Name:  ${fullName}`);
        console.log(`                 Email: ${email}`);
        console.log(`                 Role:  ${role}`);
      } else {
        await docRef.set(firestoreProfile, { merge: true });
        console.log(`[MIGRATED] UID: ${uid} | Email: ${email} | Role: ${role} | Name: ${fullName}`);
        stats.migrated++;
      }
    }

    console.log('\n======================================================================');
    console.log('                          Migration Summary                           ');
    console.log('======================================================================');
    console.log(`Total RTDB user records scanned:   ${stats.scanned}`);
    console.log(`Already present in Cloud Firestore: ${stats.alreadyInFirestore}`);
    if (isDryRun) {
      console.log(`Records ready to be migrated:      ${stats.scanned - stats.alreadyInFirestore - stats.errors}`);
      console.log('\nTo execute live migration, re-run without --dry-run:');
      console.log('  node scripts/migrate_rtdb_to_firestore.js');
    } else {
      console.log(`Successfully migrated to Firestore: ${stats.migrated}`);
      console.log('Realtime Database data status:      100% UNTOUCHED (Zero Data Loss)');
    }
    console.log(`Errors or invalid records:         ${stats.errors}`);
    console.log('======================================================================\n');

  } catch (error) {
    console.error('[ERROR] Migration failed:', error);
    process.exit(1);
  }
}

runMigration();
