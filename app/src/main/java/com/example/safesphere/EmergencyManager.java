package com.example.safesphere;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import androidx.core.app.ActivityCompat;

public class EmergencyManager {

    private static String[] currentNumbers;
    private static int currentIndex = 0;

    private static final int LIVE_MAX_MESSAGES = 5;
    private static final long LIVE_INTERVAL_MS = 60_000;

    private static int liveSentCount = 0;
    private static android.os.Handler liveHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // ---------------- CURRENT SOS ----------------
    public static void triggerEmergencyCurrent(Context ctx) {
        android.util.Log.d("EMERGENCY", "Current SOS triggered");
        liveHandler.removeCallbacksAndMessages(null);
        sendEmergency(ctx.getApplicationContext(), false);
    }

    // ---------------- LIVE SOS ----------------
    public static void triggerEmergencyLive(Context ctx) {
        android.util.Log.d("EMERGENCY", "Live SOS triggered");

        // existing logic
        liveHandler.removeCallbacksAndMessages(null);
        liveSentCount = 0;
        sendEmergency(ctx.getApplicationContext(), true);
    }

    // ---------------- COMMON EMERGENCY SEND ----------------
    private static void sendEmergency(Context ctx, boolean liveMode) {
        currentNumbers = Prefs.getEmergencyNumbers(ctx);
        currentIndex = 0;

        android.util.Log.d("EMERGENCY", "Emergency numbers: " + (currentNumbers != null ? currentNumbers.length : 0));

        String locText = buildLocationText(ctx);
        String message = "🚨 EMERGENCY! I am in danger. Please help me." + locText;

        android.util.Log.d("EMERGENCY", "Message: " + message);

        if (currentNumbers != null) {
            for (String num : currentNumbers) {
                if (num != null && !num.isEmpty()) {
                    android.util.Log.d("EMERGENCY", "Sending SMS to: " + num);
                    sendSms(ctx, num, message);
                }
            }
        }

        startNextCall(ctx);

        if (liveMode) {
            scheduleLiveLocation(ctx);
        }
    }

    // ---------------- LOCATION TEXT ----------------
    public static String buildLocationText(Context ctx) {

        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return " (Location permission denied)";
        }

        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return " (Location service unavailable)";

        Location loc = null;

        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (loc != null) {
            return " https://maps.google.com/?q=" +
                    loc.getLatitude() + "," + loc.getLongitude();
        }

        // ⭐ IMPORTANT — fresh location request
        requestFreshLocation(ctx, lm);

        return " (Getting live location… wait 5 sec)";
    }


    // Request fresh location update
    private static void requestFreshLocation(Context ctx, LocationManager lm) {

        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {

                String link = " https://maps.google.com/?q=" +
                        location.getLatitude() + "," + location.getLongitude();

                android.util.Log.d("LOCATION", "Fresh location: " + link);

                // ⭐ NEW — send SMS again with real location
                if (currentNumbers != null) {
                    for (String num : currentNumbers) {
                        if (num != null && !num.isEmpty()) {
                            sendSms(ctx, num, "🚨 LIVE LOCATION:" + link);
                        }
                    }
                }

                lm.removeUpdates(this);
            }

            public void onStatusChanged(String p, int s, Bundle b) {}
            public void onProviderEnabled(String p) {}
            public void onProviderDisabled(String p) {}
        };

        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null);
        } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null);
        }
    }


    // ---------------- SMS SENDER ----------------
    private static void sendSms(Context ctx, String number, String msg) {
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("SMS", "SMS permission denied");
            return;
        }

        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(number, null, msg, null, null);
            android.util.Log.d("SMS", "SMS sent to: " + number);
        } catch (Exception e) {
            android.util.Log.e("SMS", "Failed to send SMS to: " + number, e);
            e.printStackTrace();
        }
    }

    // ---------------- CALL SEQUENCE ----------------
    private static void startNextCall(Context ctx) {
        if (currentNumbers == null || currentIndex >= currentNumbers.length) {
            android.util.Log.d("CALL", "No more numbers to call");
            return;
        }

        String num = currentNumbers[currentIndex];
        if (num == null || num.isEmpty()) {
            android.util.Log.d("CALL", "Empty number at index: " + currentIndex);
            currentIndex++;
            startNextCall(ctx);
            return;
        }

        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("CALL", "CALL_PHONE permission denied");
            return;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + num));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(callIntent);
            android.util.Log.d("CALL", "Calling: " + num);

            registerCallListener(ctx);
        } catch (Exception e) {
            android.util.Log.e("CALL", "Failed to initiate call", e);
        }
    }

    private static void registerCallListener(Context ctx) {
        TelephonyManager tm =
                (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);

        if (tm == null) {
            android.util.Log.e("CALL", "TelephonyManager is null");
            return;
        }

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("CALL", "READ_PHONE_STATE permission denied");
            return;
        }

        tm.listen(new PhoneStateListener() {
            boolean callStarted = false;

            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    callStarted = true;
                    android.util.Log.d("CALL", "Call connected");
                } else if (state == TelephonyManager.CALL_STATE_IDLE && callStarted) {
                    android.util.Log.d("CALL", "Call ended, moving to next");
                    tm.listen(this, PhoneStateListener.LISTEN_NONE);
                    currentIndex++;
                    startNextCall(ctx);
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
    }

    // ---------------- LIVE LOCATION LOOP ----------------
    public static void scheduleLiveLocation(Context ctx) {
        android.util.Log.d("LIVE_LOCATION", "Scheduled live location updates");

        liveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (liveSentCount >= LIVE_MAX_MESSAGES) {
                    android.util.Log.d("LIVE_LOCATION", "Max messages reached");
                    return;
                }

                liveSentCount++;
                android.util.Log.d("LIVE_LOCATION", "Sending update #" + liveSentCount);

                String locText = buildLocationText(ctx);
                if (currentNumbers != null) {
                    String msg = "🚨 Live location update " + liveSentCount + ":" + locText;

                    for (String num : currentNumbers) {
                        if (num != null && !num.isEmpty()) {
                            sendSms(ctx, num, msg);
                        }
                    }
                }

                if (liveSentCount < LIVE_MAX_MESSAGES) {
                    liveHandler.postDelayed(this, LIVE_INTERVAL_MS);
                }
            }
        }, LIVE_INTERVAL_MS);
    }
}