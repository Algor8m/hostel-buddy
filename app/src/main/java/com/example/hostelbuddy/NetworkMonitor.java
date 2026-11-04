package com.example.hostelbuddy;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors physical device network connectivity changes in real time.
 * Distinguishes Wi-Fi, Cellular, and offline states, and notifies active listeners.
 */
public final class NetworkMonitor {

    private static final String TAG = "HostelBuddyNet";
    private static volatile NetworkMonitor instance;

    private final Context appContext;
    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<ConnectivityListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile boolean isConnected = false;
    private volatile boolean isWifi = false;
    private volatile boolean isCellular = false;
    private ConnectivityManager.NetworkCallback networkCallback;

    public interface ConnectivityListener {
        void onConnectivityChanged(boolean isConnected, boolean isWifi, boolean isCellular);
    }

    private NetworkMonitor(Context context) {
        this.appContext = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        checkInitialState();
        registerNetworkCallback();
    }

    public static NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (NetworkMonitor.class) {
                if (instance == null) {
                    instance = new NetworkMonitor(context);
                }
            }
        }
        return instance;
    }

    private void checkInitialState() {
        if (connectivityManager == null) {
            isConnected = false;
            return;
        }

        try {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities != null) {
                    isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                    isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "ACCESS_NETWORK_STATE permission missing or restricted: " + e.getMessage());
            isConnected = true; // Assume true if permission restricted to avoid false negatives
        }
        Log.i(TAG, "Initial network state: connected=" + isConnected + ", wifi=" + isWifi + ", cellular=" + isCellular);
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateState(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateState(false);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                boolean validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                boolean wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                boolean cell = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                updateCapabilities(validated, wifi, cell);
            }
        };

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Exception e) {
            Log.e(TAG, "Could not register NetworkCallback: " + e.getMessage());
        }
    }

    private synchronized void updateState(boolean available) {
        if (connectivityManager == null) {
            isConnected = available;
            notifyListeners();
            return;
        }

        try {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities != null) {
                    isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                } else {
                    isConnected = available;
                }
            } else {
                isConnected = available;
                isWifi = false;
                isCellular = false;
            }
        } catch (SecurityException e) {
            isConnected = available;
        }
        Log.d(TAG, "Network state updated: connected=" + isConnected + ", wifi=" + isWifi + ", cell=" + isCellular);
        notifyListeners();
    }

    private synchronized void updateCapabilities(boolean validated, boolean wifi, boolean cell) {
        this.isConnected = validated;
        this.isWifi = wifi;
        this.isCellular = cell;
        Log.d(TAG, "Capabilities changed: validated=" + validated + ", wifi=" + wifi + ", cell=" + cell);
        notifyListeners();
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (ConnectivityListener listener : listeners) {
                try {
                    listener.onConnectivityChanged(isConnected, isWifi, isCellular);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying connectivity listener: " + e.getMessage());
                }
            }
        });
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isWifi() {
        return isWifi;
    }

    public boolean isCellular() {
        return isCellular;
    }

    public void addListener(ConnectivityListener listener) {
        if (listener != null) {
            listeners.add(listener);
            listener.onConnectivityChanged(isConnected, isWifi, isCellular);
        }
    }

    public void removeListener(ConnectivityListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
