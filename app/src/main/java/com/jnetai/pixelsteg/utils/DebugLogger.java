package com.jnetai.pixelsteg.utils;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugLogger {

    private static final String TAG = "DebugLogger";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static boolean enabled = true;

    public static void log(String tag, String message) {
        if (enabled) {
            String timestamp = sdf.format(new Date());
            String formattedMessage = "[" + timestamp + "] [" + tag + "] " + message;
            Log.d(TAG, formattedMessage);
        }
    }

    public static void setEnabled(boolean enabled) {
        DebugLogger.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
