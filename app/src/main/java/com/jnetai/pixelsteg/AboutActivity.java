package com.jnetai.pixelsteg;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.jnetai.pixelsteg.utils.ErrorHandler;
import com.jnetai.pixelsteg.utils.DebugLogger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";
    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/jnetai-clawbot/PixelSteg/releases/latest";

    private TextView tvVersion;
    private TextView tvAbout;
    private Button btnCheckUpdate;
    private Button btnShareApp;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_about);

            DebugLogger.log(TAG, "AboutActivity onCreate started");
            executor = Executors.newSingleThreadExecutor();

            tvVersion = findViewById(R.id.tvVersion);
            tvAbout = findViewById(R.id.tvAbout);
            btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
            btnShareApp = findViewById(R.id.btnShareApp);

            String versionName = "1.0.0";
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                versionName = pInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                ErrorHandler.handle(TAG, "ERR-ABOUT-001", "Failed to get package info", e, this);
            }

            tvVersion.setText("Version: " + versionName);
            tvAbout.setText("Pixel Steganography\n\nHide and encrypt files inside images using LSB steganography.\n\nMade by jnetai.com");

            String finalVersionName = versionName;
            btnCheckUpdate.setOnClickListener(v -> checkForUpdate(finalVersionName));
            btnShareApp.setOnClickListener(v -> shareApp());

            DebugLogger.log(TAG, "AboutActivity onCreate completed");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-ABOUT-002", "Failed to initialize AboutActivity", e, this);
        }
    }

    private void checkForUpdate(String currentVersion) {
        try {
            btnCheckUpdate.setEnabled(false);
            btnCheckUpdate.setText("Checking...");
            DebugLogger.log(TAG, "Checking for updates, current version: " + currentVersion);

            executor.execute(() -> {
                try {
                    URL url = new URL(GITHUB_RELEASES_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        String json = response.toString();
                        String latestTag = extractTag(json);

                        new Handler(Looper.getMainLooper()).post(() -> {
                            btnCheckUpdate.setEnabled(true);
                            btnCheckUpdate.setText("Check for Update");
                            if (latestTag != null && !latestTag.isEmpty()) {
                                if (!latestTag.equals(currentVersion)) {
                                    Toast.makeText(AboutActivity.this,
                                        "New version available: " + latestTag + "\nCurrent: " + currentVersion,
                                        Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(AboutActivity.this,
                                        "You are up to date! (v" + currentVersion + ")",
                                        Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(AboutActivity.this,
                                    "Could not determine latest version",
                                    Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            btnCheckUpdate.setEnabled(true);
                            btnCheckUpdate.setText("Check for Update");
                            Toast.makeText(AboutActivity.this,
                                "Update check failed (HTTP " + responseCode + ")",
                                Toast.LENGTH_SHORT).show();
                        });
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    ErrorHandler.handle(TAG, "ERR-ABOUT-003", "Update check failed", e, this);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        btnCheckUpdate.setEnabled(true);
                        btnCheckUpdate.setText("Check for Update");
                        Toast.makeText(AboutActivity.this, "Update check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-ABOUT-004", "Failed to start update check", e, this);
        }
    }

    private String extractTag(String json) {
        try {
            int tagIdx = json.indexOf("\"tag_name\"");
            if (tagIdx >= 0) {
                int start = json.indexOf("\"", tagIdx + 11) + 1;
                int end = json.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-ABOUT-005", "Failed to extract tag from JSON", e, this);
        }
        return null;
    }

    private void shareApp() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Pixel Steganography");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Check out Pixel Steganography - Hide files inside images!\n\n" +
                "https://github.com/jnetai-clawbot/PixelSteg/releases\n\n" +
                "Made by jnetai.com");
            startActivity(Intent.createChooser(shareIntent, "Share via"));
            DebugLogger.log(TAG, "Share intent sent");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-ABOUT-006", "Failed to share app", e, this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
