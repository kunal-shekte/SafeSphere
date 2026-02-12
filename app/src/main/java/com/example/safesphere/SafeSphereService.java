package com.example.safesphere;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SafeSphereService extends Service implements ShakeDetector.OnShakeListener {

    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "safesphere_channel";

    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;

    private Model voskModel;
    private Recognizer recognizer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ================= SERVICE START =================
    @Override
    public void onCreate() {
        android.util.Log.e("SERVICE_DEBUG", "========== SERVICE onCreate CALLED ==========");
        android.widget.Toast.makeText(this, "Service onCreate called!", android.widget.Toast.LENGTH_LONG).show();
        super.onCreate();

        // CHECK MICROPHONE PERMISSION FIRST
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("SERVICE", "No microphone permission!");
            stopSelf();
            return;
        }

        android.util.Log.d("SERVICE_TEST", "SafeSphereService started");

        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notification);
        }

        // Shake detection
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector(this);
        sensorManager.registerListener(shakeDetector, accel, SensorManager.SENSOR_DELAY_UI);
        android.util.Log.d("SHAKE_DETECTOR", "Shake detector registered");

        // Start Vosk
        initVosk();
    }

    // ================= COPY MODEL (RECURSIVE) =================


    private void copyModelFromAssets() {
        android.util.Log.e("VOSK_DEBUG", "copyModelFromAssets() START");
        try {
            // Debug: List all assets
            String[] rootAssets = getAssets().list("");
            android.util.Log.e("VOSK_DEBUG", "Root assets: " + java.util.Arrays.toString(rootAssets));

            String[] modelAssets = getAssets().list("model-android");
            if (modelAssets == null || modelAssets.length == 0) {
               android.util.Log.e("VOSK_DEBUG", "❌ ERROR: 'model-android' folder not found in assets or empty!");
            } else {
               android.util.Log.e("VOSK_DEBUG", "'model-android' contains: " + java.util.Arrays.toString(modelAssets));
            }

            File dir = new File(getFilesDir(), "model-android");
            if (dir.exists() && dir.list() != null && dir.list().length > 0) {
                android.util.Log.e("VOSK_DEBUG", "✅ Model already exists and is not empty. Skipping copy.");
                return;
            }

            android.util.Log.e("VOSK_DEBUG", "📂 Copying model from assets to: " + dir.getAbsolutePath());
            
            // UI Thread for Toast
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                android.widget.Toast.makeText(getApplicationContext(), "Copying Vosk model...", android.widget.Toast.LENGTH_SHORT).show()
            );

            dir.mkdirs();
            copyAssetFolder(getAssets(), "model-android", dir.getAbsolutePath());
            android.util.Log.e("VOSK_DEBUG", "✅ Model copied successfully");

        } catch (Exception e) {
            android.util.Log.e("VOSK_DEBUG", "❌ Error copying model", e);
            e.printStackTrace();
        }
    }

    private void copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            new File(toPath).mkdirs();

            for (String file : files) {
                String[] subFiles = assetManager.list(fromAssetPath + "/" + file);
                if (subFiles != null && subFiles.length == 0) {
                    // It's a file
                    copyAssetFile(assetManager, fromAssetPath + "/" + file,
                            toPath + "/" + file);
                } else {
                    // It's a directory
                    copyAssetFolder(assetManager, fromAssetPath + "/" + file,
                            toPath + "/" + file);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VOSK_MODEL", "Error in copyAssetFolder", e);
            e.printStackTrace();
        }
    }

    private void copyAssetFile(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            InputStream in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            FileOutputStream out = new FileOutputStream(toPath);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();
        } catch (Exception e) {
            android.util.Log.e("VOSK_MODEL", "Error copying file: " + fromAssetPath, e);
            e.printStackTrace();
        }
    }

    // ================= INIT VOSK =================
    private void initVosk() {
        executor.execute(() -> {
            try {
                android.util.Log.d("VOSK_INIT", "Starting Vosk initialization...");
                copyModelFromAssets();

                File modelPath = new File(getFilesDir(), "model-android");
                android.util.Log.d("VOSK_INIT", "Model path: " + modelPath.getAbsolutePath());

                voskModel = new Model(modelPath.getAbsolutePath());
                recognizer = new Recognizer(voskModel, 16000.0f);

                android.util.Log.d("VOSK_INIT", "Vosk model loaded successfully");

                startListening();

            } catch (Exception e) {
                android.util.Log.e("VOSK_INIT", "Failed to initialize Vosk", e);
                e.printStackTrace();
            }
        });
    }

    // ================= MIC LISTEN =================
    private void startListening() {
        try {
            android.util.Log.d("VOSK_LISTEN", "Starting audio recording...");

            int bufferSize = AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (bufferSize <= 0) {
                bufferSize = 4096;
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("VOSK_LISTEN", "No RECORD_AUDIO permission");
                return;
            }

            AudioRecord recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            recorder.startRecording();
            android.util.Log.d("VOSK_LISTEN", "AudioRecord started, listening for keyword...");

            byte[] buffer = new byte[bufferSize];

            while (!executor.isShutdown()) {
                int nread = recorder.read(buffer, 0, buffer.length);

                if (nread > 0 && recognizer != null) {
                    if (recognizer.acceptWaveForm(buffer, nread)) {
                        String result = recognizer.getResult();
                        checkKeyword(result);
                    }
                }
            }

            recorder.stop();
            recorder.release();
            android.util.Log.d("VOSK_LISTEN", "Audio recording stopped");

        } catch (Exception e) {
            android.util.Log.e("VOSK_LISTEN", "Error in audio recording", e);
            e.printStackTrace();
        }
    }

    // ================= KEYWORD CHECK =================

    private void checkKeyword(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString("text", "").toLowerCase().trim();

            android.util.Log.d("VOSK_TEXT", "Detected: '" + text + "'");

            String keyword = Prefs.getKeyword(this).toLowerCase().trim();
            android.util.Log.d("VOSK_KEYWORD", "Looking for: '" + keyword + "'");

            if (!keyword.isEmpty() && text.contains(keyword)) {
                android.util.Log.d("VOSK_MATCH", "🚨 KEYWORD MATCHED! Triggering emergency");
                android.widget.Toast.makeText(this, "Keyword detected! Triggering SOS", android.widget.Toast.LENGTH_SHORT).show();
                EmergencyManager.triggerEmergencyLive(this);
            }

        } catch (JSONException e) {
            android.util.Log.e("VOSK_KEYWORD", "Error parsing JSON", e);
            e.printStackTrace();
        }
    }

    // ================= NOTIFICATION =================
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "SafeSphere Emergency Listener",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeSphere active")
                .setContentText("Listening for keyword and shakes.")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setOngoing(true)
                .build();
    }

    // ================= SERVICE CONFIG =================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("SERVICE_TEST", "SafeSphereService destroyed");

        executor.shutdownNow();

        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }

        if (recognizer != null) recognizer.close();
        if (voskModel != null) voskModel.close();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ================= SHAKE TRIGGER =================
    @Override
    public void onShakeTriggered() {
        android.util.Log.d("SHAKE_DETECTOR", "🚨 SHAKE DETECTED! Triggering emergency");
        android.widget.Toast.makeText(this, "Shake detected! Triggering SOS", android.widget.Toast.LENGTH_SHORT).show();
        EmergencyManager.triggerEmergencyLive(this);
    }
}