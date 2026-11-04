## Important before testing Campus Cravings

The current Campus Cravings screen writes to `foodTruck/items`. If Firebase Realtime Database rules still refer to `campusCravings`, saving will fail with `Permission denied`. Publish the rules in the section below, using the exact paths shown (`foodTruck`, `messMenu`). The category selector is explicitly configured as a dropdown in this build.

# Hostel Buddy — implementation notes

## This increment: Mess & Campus Cravings administration

- Added a real admin dashboard with buttons for mess menu management and Campus Cravings management.
- Added a mess admin screen that can import a CSV, load/edit a selected week/day/meal, update a venue, and mark a selected day's dinner as special.
- Added Campus Cravings item management: add/edit item number, name, price, veg/non-veg category, availability; toggle availability; remove items.
- The student-facing readers and the admin screens use the existing database paths `messMenu` and `foodTruck`. The student Campus Cravings screen remains view-only.
- Kept the existing Java/Firebase setup and the configured Singapore Realtime Database URL.

## Firebase roles

Each Authentication account needs a role at `users/<Firebase Authentication UID>/role`.
Supported values: `student`, `laundry_admin`, `mess_admin`.
Do not let clients write their own roles. Assign roles in Firebase Console or through a trusted admin service.

## Required Realtime Database rules for the current paths

In Firebase Console → Realtime Database → Rules, publish the following rules for the current application data paths. These allow signed-in users to read menus, restrict menu writes to `mess_admin`, allow users to read their own profile/role, and allow authenticated users to read laundry orders (the laundry rules will need to be refined when admin order-management is implemented).

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": false
      }
    },
    "messMenu": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'mess_admin'"
    },
    "foodTruck": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'mess_admin'"
    },
    "laundryRates": {
      ".read": "auth != null",
      ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'laundry_admin'"
    },
    "laundryOrders": {
      "$uid": {
        ".read": "auth != null && (auth.uid === $uid || root.child('users').child(auth.uid).child('role').val() === 'laundry_admin')",
        ".write": "auth != null && root.child('users').child(auth.uid).child('role').val() === 'laundry_admin'"
      }
    }
  }
}
```

These rules are a development baseline for the current app. Before real campus deployment, laundry order reads/writes should be restricted by user UID and admin role, and additional data validation should be added. Do not use public read/write rules.

## Mess CSV format

The importer expects a header row with `week` and `day`. Supported meal columns are `breakfast`, `lunch`, `snacks`, and `dinner`; optional columns are `venue` and `specialDinner`. Use `1` / `2` (or `week1` / `week2`) for the week and full day names for day.

Example:

```csv
week,day,breakfast,lunch,snacks,dinner,venue,specialDinner
1,Monday,"Aloo Paratha, Curd, Tea","Chana Masala, Rice","Tea, Fruits","Dal Makhani, Chapati",Main Mess,
1,Tuesday,"Idli, Sambar","Mix Veg, Rice","Maggi","Paneer, Chapati",Main Mess,
2,Monday,"Poha, Tea","Rajma, Rice","Sandwich","Veg Pulao, Raita",Main Mess,
```

Use one row per day (14 rows for a full two-week menu). The importer updates the rows represented by the CSV; it does not delete unrelated existing menu records.

## Campus Cravings data shape

Items are stored at `foodTruck/items/<item-key>` with fields:
- `itemNumber` (string)
- `name` (string)
- `price` (number)
- `category` (`Veg` or `Non Veg`)
- `available` (boolean)

Optional `foodTruck/timings` and `foodTruck/venue` fields can be managed in Firebase Console until their admin form is added.

## Existing project configuration

- Language: Java
- Firebase Authentication and Realtime Database
- Database URL: `https://hostel-buddy-ar12-default-rtdb.asia-southeast1.firebasedatabase.app`
- Existing SDK/Gradle settings preserved.

## Build validation

This environment may not be able to download the configured Gradle distribution. Open the project in Android Studio, sync Gradle, and run `app` to validate on the emulator. If compilation fails, share the first build error from the Build output.
