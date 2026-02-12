package com.example.safesphere;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 101;

    private MediaPlayer sirenPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissionsIfNeeded();

        Toast.makeText(this, "MainActivity started", Toast.LENGTH_SHORT).show();

        //startSafeSphereService();

        // ---------- VIEWS ----------
        Button btnSosCurrent = findViewById(R.id.btnSosCurrent);
        Button btnSosLive = findViewById(R.id.btnSosLive);
        Button btnSiren = findViewById(R.id.btnSiren);
        Button btnFakeCall = findViewById(R.id.btnFakeCall);
        ImageButton btnProfile = findViewById(R.id.btnProfile);

        // Sab permissions check + service start
        ensurePermissions();

        // ---------- CLICK LISTENERS ----------

        // Dashboard: SHARE current location (opens chooser)
        btnSosCurrent.setOnClickListener(v -> {
            if (ensurePermissions()) {
                shareLocation(false);
            }
        });

        // Dashboard: SHARE live location (opens chooser)
        btnSosLive.setOnClickListener(v -> {
            if (ensurePermissions()) {
                shareLocation(true);
            }
        });

        // 3) Siren
        btnSiren.setOnClickListener(v -> toggleSiren());

        // 4) Fake call screen
        btnFakeCall.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FakeCallActivity.class)));

        // 5) Profile screen
        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        startSafeSphereService();
    }


    // ---------- SHARE LOCATION (CURRENT / LIVE) ----------

    private void shareLocation(boolean live) {

        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);

        boolean enabled = lm != null &&
                (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                        || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));

        // ❌ Location OFF → popup + settings open
        if (!enabled) {
            Toast.makeText(this, "Please turn ON location first", Toast.LENGTH_LONG).show();

            // Open location settings screen
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        // ✅ Location ON → normal sharing
        String locText = EmergencyManager.buildLocationText(this);

        String prefix = live
                ? "Sharing my live location (open link to track): "
                : "My current location: ";

        String text = prefix + locText;

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);

        startActivity(Intent.createChooser(
                sendIntent,
                live ? "Share live location via" : "Share current location via"
        ));
    }

    // ---------- PERMISSIONS + SERVICE ----------

    /**
     * SMS + CALL + LOCATION + MIC + PHONE_STATE permissions check karega.
     * Agar kuch missing ho to request karega.
     * Sab mil gaye to background service start karega.
     */
    private boolean ensurePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Old devices – direct start
            startSafeSphereService();
            return true;
        }

        String[] needed = new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE
        };

        List<String> toRequest = new ArrayList<>();
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    toRequest.toArray(new String[0]),
                    REQ_PERMISSIONS
            );
            return false;
        } else {
            // Sab permissions mil chuki hain → service ensure karo
            startSafeSphereService();
            return true;
        }
    }

    /**
     * Mic permission granted ho to keyword+shake service ko foreground me start karo.
     */
    private void startSafeSphereService() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

            Intent serviceIntent = new Intent(this, SafeSphereService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted for SOS & keyword.", Toast.LENGTH_SHORT).show();
                startSafeSphereService();
            } else {
                Toast.makeText(this,
                        "Some permissions denied. Share & keyword features may be limited.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- SIREN LOGIC ----------

    private void toggleSiren() {
        if (sirenPlayer == null) {
            try {
                sirenPlayer = MediaPlayer.create(this, R.raw.siren);
                if (sirenPlayer == null) {
                    Toast.makeText(this, "Siren audio not found", Toast.LENGTH_SHORT).show();
                    return;
                }
                sirenPlayer.setLooping(true);
                sirenPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to play siren", Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                if (sirenPlayer.isPlaying()) {
                    sirenPlayer.stop();
                }
            } catch (Exception ignored) {
            }
            sirenPlayer.release();
            sirenPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sirenPlayer != null) {
            sirenPlayer.release();
            sirenPlayer = null;
        }
    }

    private void requestPermissionsIfNeeded() {
        String[] perms = {
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.POST_NOTIFICATIONS
        };

        androidx.core.app.ActivityCompat.requestPermissions(this, perms, 1);
    }
}
