package com.example.hostelbuddy;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative single source of truth for all mess menu and live Now Serving data.
 * Keeps an active real-time Firestore listener, computes periodic status transitions,
 * and executes atomic versioned CSV publication.
 */
public final class MessMenuRepository {

    private static final String TAG = "MessMenuRepo";
    private static volatile MessMenuRepository instance;

    public interface NowServingListener {
        void onNowServingUpdated(@NonNull NowServingService.NowServingInfo info);
        void onMenuError(@NonNull String errorMessage);
    }

    public interface ImportCallback {
        void onSuccess(int totalPublished, @NonNull String batchId);
        void onFailure(@NonNull String errorMessage);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, MessMenuDay> cachedMenu = new ConcurrentHashMap<>();
    private final Set<NowServingListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ListenerRegistration firestoreRegistration;
    private Runnable tickerRunnable;
    private boolean isListening = false;
    private boolean isFromLiveServer = false;

    private MessMenuRepository() {
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            if (auth.getCurrentUser() != null) {
                restartFirestoreListener();
            } else {
                stopFirestoreListener();
                cachedMenu.clear();
            }
        });
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            startFirestoreListener();
        }
        startTicker();
    }

    public synchronized void restartFirestoreListener() {
        stopFirestoreListener();
        startFirestoreListener();
    }

    public synchronized void stopFirestoreListener() {
        if (firestoreRegistration != null) {
            firestoreRegistration.remove();
            firestoreRegistration = null;
        }
    }

    public static MessMenuRepository getInstance() {
        if (instance == null) {
            synchronized (MessMenuRepository.class) {
                if (instance == null) {
                    instance = new MessMenuRepository();
                }
            }
        }
        return instance;
    }

    private synchronized void startFirestoreListener() {
        if (firestoreRegistration != null) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        firestoreRegistration = FirestoreConfig.getFirestore()
                .collection(FirestoreConfig.COLLECTION_MESS_MENU)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore menu listener error: " + error.getMessage());
                        notifyError(FirebaseDiagnosticHelper.classifyError(null, error, "Listening to Mess Menu"));
                        return;
                    }

                    if (snapshots != null) {
                        isFromLiveServer = !snapshots.getMetadata().isFromCache();
                        cachedMenu.clear();

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            MessMenuDay day = new MessMenuDay();
                            day.setWeek(doc.getString("week"));
                            day.setDay(doc.getString("day"));
                            day.setDayKey(doc.getString("dayKey"));
                            day.setBreakfast(doc.getString("breakfast"));
                            day.setLunch(doc.getString("lunch"));
                            day.setSnacks(doc.getString("snacks"));
                            day.setDinner(doc.getString("dinner"));
                            day.setVenue(doc.getString("venue"));
                            day.setSpecialDinner(doc.getString("specialDinner"));
                            day.setSpecialDinnerVenue(doc.getString("specialDinnerVenue"));
                            day.setBreakfastHours(doc.getString("breakfastHours"));
                            day.setLunchHours(doc.getString("lunchHours"));
                            day.setSnacksHours(doc.getString("snacksHours"));
                            day.setDinnerHours(doc.getString("dinnerHours"));

                            cachedMenu.put(doc.getId().toLowerCase(Locale.ROOT), day);
                        }
                        Log.d(TAG, "Mess menu cache updated: " + cachedMenu.size() + " days (live=" + isFromLiveServer + ")");
                        recomputeAndNotify();
                    }
                });
    }

    private void startTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                recomputeAndNotify();
                // Tick every 30 seconds to refresh remaining time and meal transitions
                mainHandler.postDelayed(this, 30_000);
            }
        };
        mainHandler.postDelayed(tickerRunnable, 30_000);
    }

    public synchronized void recomputeAndNotify() {
        Calendar cal = NowServingService.getCampusCalendar();
        String dayKey = NowServingService.getCurrentDayKey(cal);
        String weekKey = NowServingService.getCurrentWeekKey(cal);
        String tomorrowKey = NowServingService.getNextDayKey(dayKey);

        String todayDocId = (weekKey + "_" + dayKey).toLowerCase(Locale.ROOT);
        String tomorrowDocId = (weekKey + "_" + tomorrowKey).toLowerCase(Locale.ROOT);

        MessMenuDay today = cachedMenu.get(todayDocId);
        MessMenuDay tomorrow = cachedMenu.get(tomorrowDocId);

        NowServingService.NowServingInfo info = NowServingService.computeNowServing(today, tomorrow, isFromLiveServer);

        mainHandler.post(() -> {
            for (NowServingListener listener : listeners) {
                try {
                    listener.onNowServingUpdated(info);
                } catch (Exception e) {
                    Log.e(TAG, "Error in NowServing listener: " + e.getMessage());
                }
            }
        });
    }

    private void notifyError(String error) {
        mainHandler.post(() -> {
            for (NowServingListener listener : listeners) {
                try {
                    listener.onMenuError(error);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying error listener: " + e.getMessage());
                }
            }
        });
    }

    public void addListener(NowServingListener listener) {
        if (listener != null) {
            listeners.add(listener);
            recomputeAndNotify();
        }
    }

    public void removeListener(NowServingListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Atomically publishes the imported menu to Cloud Firestore.
     * Uses WriteBatch and writes active version metadata to prevent partial reads.
     */
    public void publishImportedMenu(@NonNull List<MessMenuDay> menuDays,
                                    @NonNull String adminUid,
                                    @NonNull ImportCallback callback) {
        if (menuDays.isEmpty()) {
            callback.onFailure("Cannot publish an empty menu list.");
            return;
        }

        WriteBatch batch = FirestoreConfig.getFirestore().batch();
        String batchId = "batch_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Write each day's menu document
        for (MessMenuDay day : menuDays) {
            String docId = day.getDocumentId();
            DocumentReference docRef = FirestoreConfig.getFirestore()
                    .collection(FirestoreConfig.COLLECTION_MESS_MENU).document(docId);

            Map<String, Object> data = day.toMap();
            data.put("updatedAt", FieldValue.serverTimestamp());
            data.put("updatedBy", adminUid);
            data.put("batchId", batchId);

            batch.set(docRef, data, SetOptions.merge());
        }

        // 2. Write metadata document marking this import version active
        DocumentReference metaRef = FirestoreConfig.getFirestore()
                .collection(FirestoreConfig.COLLECTION_MESS_MENU_META)
                .document(FirestoreConfig.DOC_ACTIVE_MENU);

        Map<String, Object> meta = new HashMap<>();
        meta.put("activeBatchId", batchId);
        meta.put("totalDaysPublished", menuDays.size());
        meta.put("publishedAt", FieldValue.serverTimestamp());
        meta.put("publishedBy", adminUid);

        batch.set(metaRef, meta, SetOptions.merge());

        // Commit atomically
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "Atomic menu publication succeeded. Batch: " + batchId);
                    callback.onSuccess(menuDays.size(), batchId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Atomic menu publication failed: " + e.getMessage());
                    callback.onFailure(e.getMessage() != null ? e.getMessage() : "Batch commit failed.");
                });
    }
}
