/**
 * Secure Administrator Provisioning Script for Hostel Buddy (Firebase Project: hostel-buddy-ar12)
 *
 * This script provisions the initial administrative accounts without hardcoding passwords:
 * - Creates the user in Firebase Authentication (if not present)
 * - Assigns the specified administrative role in Cloud Firestore (`users/{uid}`)
 * - Generates a secure one-time password setup/reset link for the administrator
 *
 * Usage:
 *   node scripts/provision_admin.js --email=<admin_email> --role=<mess_admin|laundry_admin|food_truck_admin> [--name="Admin Name"] [--key=path/to/serviceAccountKey.json]
 *
 * Supported roles:
 *   - mess_admin
 *   - laundry_admin
 *   - food_truck_admin
 */

const fs = require('fs');
const path = require('path');

// Parse CLI args
const args = {};
process.argv.slice(2).forEach(arg => {
  if (arg.startsWith('--')) {
    const [key, ...vals] = arg.substring(2).split('=');
    args[key] = vals.join('=');
  }
});

const email = args.email;
const role = args.role;
const name = args.name || 'Hostel Buddy Administrator';
const keyPath = args.key || process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'serviceAccountKey.json');

const VALID_ROLES = ['mess_admin', 'laundry_admin', 'food_truck_admin'];

console.log('===============================================================');
console.log('       Hostel Buddy - Secure Administrator Provisioning        ');
console.log('===============================================================\n');

if (!email || !role) {
  console.error('Error: Missing required arguments.\n');
  console.log('Usage:');
  console.log('  node scripts/provision_admin.js --email=admin@example.com --role=mess_admin [--name="Mess Admin"]\n');
  console.log('Available roles:');
  console.log('  • mess_admin        - Can manage rotating mess menus, special dinners, and venues');
  console.log('  • laundry_admin     - Can manage student laundry orders and rates');
  console.log('  • food_truck_admin  - Can manage Campus Cravings menu items and live availability\n');
  process.exit(1);
}

if (!VALID_ROLES.includes(role)) {
  console.error(`Error: Invalid role "${role}". Supported roles: ${VALID_ROLES.join(', ')}\n`);
  process.exit(1);
}

// Check for service account key
if (!fs.existsSync(keyPath)) {
  console.warn(`[WARNING] Service account key not found at: ${keyPath}`);
  console.warn('To run this provisioning script:');
  console.warn('  1. In Firebase Console > Project Settings > Service Accounts, click "Generate new private key".');
  console.warn('  2. Save the downloaded file as "scripts/serviceAccountKey.json" (or provide --key=path/to/key.json).');
  console.warn('  3. Re-run this script.\n');
  console.warn('Alternatively, you can provision the admin account manually in Firebase Console:');
  console.warn('  1. Go to Firebase Console > Authentication > Users > Add User.');
  console.warn(`  2. Add "${email}". Copy the generated UID.`);
  console.warn('  3. Go to Firestore Database > users collection > Add Document with ID = [UID].');
  console.warn(`  4. Add fields: uid=[UID], email="${email}", fullName="${name}", role="${role}".\n`);
  process.exit(1);
}

let admin;
try {
  admin = require('firebase-admin');
} catch (e) {
  console.error('Error: firebase-admin is not installed. Run: npm install firebase-admin --prefix scripts\n');
  process.exit(1);
}

const serviceAccount = require(path.resolve(keyPath));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'hostel-buddy-ar12'
});

const auth = admin.auth();
const db = admin.firestore();

async function provisionAdmin() {
  try {
    let userRecord;
    try {
      userRecord = await auth.getUserByEmail(email);
      console.log(`[OK] Found existing Firebase Authentication user: ${userRecord.uid}`);
    } catch (err) {
      if (err.code === 'auth/user-not-found') {
        console.log(`[INFO] Creating new Firebase Auth account for: ${email}`);
        userRecord = await auth.createUser({
          email: email,
          displayName: name,
          emailVerified: true
        });
        console.log(`[OK] Created Authentication user with UID: ${userRecord.uid}`);
      } else {
        throw err;
      }
    }

    const uid = userRecord.uid;

    // Generate secure password setup link (no plaintext passwords!)
    const setupLink = await auth.generatePasswordResetLink(email);

    // Write role and profile to Cloud Firestore
    const userDocRef = db.collection('users').document(uid);
    await userDocRef.set({
      uid: uid,
      email: email,
      fullName: name,
      role: role,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    console.log(`[OK] Firestore profile provisioned for user ${uid} with role: "${role}"`);
    console.log('\n---------------------------------------------------------------');
    console.log('Administrator account successfully provisioned!');
    console.log(`  Email: ${email}`);
    console.log(`  Role:  ${role}`);
    console.log(`  UID:   ${uid}`);
    console.log('---------------------------------------------------------------');
    console.log('\n[SECURE ACTION LINK]');
    console.log('Send the following password-setup link to the administrator so they can set their password:');
    console.log(`\n  ${setupLink}\n`);
    console.log('This link expires according to your Firebase Auth settings and avoids exposing passwords.');
  } catch (error) {
    console.error('[ERROR] Failed to provision administrator:', error.message || error);
    process.exit(1);
  }
}

provisionAdmin();
