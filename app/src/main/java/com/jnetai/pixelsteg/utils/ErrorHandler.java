package com.jnetai.pixelsteg.utils;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ErrorHandler {

    private static final String TAG = "ErrorHandler";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static void handle(String sourceTag, String errorCode, String message, Exception exception, Activity activity) {
        StringBuilder errorLog = new StringBuilder();
        errorLog.append("[").append(sdf.format(new Date())).append("] ");
        errorLog.append("[").append(errorCode).append("] ");
        errorLog.append("[").append(sourceTag).append("] ");
        errorLog.append(message);

        if (exception != null) {
            errorLog.append("\nException: ").append(exception.getClass().getName());
            errorLog.append("\nMessage: ").append(exception.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            errorLog.append("\nStack Trace:\n").append(sw.toString());
        }

        Log.e(TAG, errorLog.toString());

        if (activity != null) {
            activity.runOnUiThread(() -> {
                String toastMsg = "[" + errorCode + "] " + message;
                if (exception != null) {
                    toastMsg += "\n" + exception.getMessage();
                }
                Toast.makeText(activity, toastMsg, Toast.LENGTH_LONG).show();
            });
        }
    }
}
