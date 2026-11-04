package com.example.hostelbuddy;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Handles incoming Firebase Cloud Messaging notifications for Hostel Buddy.
 * Listens for order status changes (Order Confirmed, Ready for Collection).
 */
public class HostelBuddyMessagingService extends FirebaseMessagingService {

    private static final String TAG = "HostelBuddyFCM";
    public static final String CHANNEL_ID_LAUNDRY = "channel_hostelbuddy_laundry";
    public static final String CHANNEL_NAME_LAUNDRY = "Laundry Status Alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token received: " + token);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FcmHelper.syncTokenToFirestore(user.getUid(), token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        String title = "Hostel Buddy";
        String messageBody = "You have an update regarding your laundry service.";

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                messageBody = remoteMessage.getNotification().getBody();
            }
        }

        Map<String, String> data = remoteMessage.getData();
        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body")) messageBody = data.get("body");

        sendNotification(title, messageBody);
    }

    private void sendNotification(String title, String messageBody) {
        Intent intent = new Intent(this, LaundryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_LAUNDRY,
                    CHANNEL_NAME_LAUNDRY,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications about laundry order confirmation and collection status");
            channel.enableLights(true);
            channel.setLightColor(Color.parseColor("#7A0019"));
            channel.enableVibration(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID_LAUNDRY)
                        .setSmallIcon(R.drawable.ic_bell_reminder)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setColor(Color.parseColor("#7A0019"))
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }
}
