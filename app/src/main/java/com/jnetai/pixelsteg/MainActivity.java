package com.jnetai.pixelsteg;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.jnetai.pixelsteg.stego.StegoEngine;
import com.jnetai.pixelsteg.utils.ErrorHandler;
import com.jnetai.pixelsteg.utils.DebugLogger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button btnSelectImage;
    private Button btnSelectFile;
    private Button btnHide;
    private Button btnExtract;
    private Button btnSaveOutput;
    private Button btnAbout;
    private EditText etPassword;
    private ImageView ivPreview;
    private TextView tvStatus;
    private ProgressBar progressBar;

    private Bitmap currentImage;
    private byte[] currentFileData;
    private String currentFileName;
    private Bitmap outputImage;
    private byte[] extractedData;
    private String extractedFileName;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            DebugLogger.log(TAG, "MainActivity onCreate started");

            initViews();
            initPickers();

            DebugLogger.log(TAG, "MainActivity onCreate completed");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-001", "Failed to initialize MainActivity", e, this);
        }
    }

    private void initViews() {
        try {
            btnSelectImage = findViewById(R.id.btnSelectImage);
            btnSelectFile = findViewById(R.id.btnSelectFile);
            btnHide = findViewById(R.id.btnHide);
            btnExtract = findViewById(R.id.btnExtract);
            btnSaveOutput = findViewById(R.id.btnSaveOutput);
            btnAbout = findViewById(R.id.btnAbout);
            etPassword = findViewById(R.id.etPassword);
            ivPreview = findViewById(R.id.ivPreview);
            tvStatus = findViewById(R.id.tvStatus);
            progressBar = findViewById(R.id.progressBar);

            btnSelectImage.setOnClickListener(v -> pickImage());
            btnSelectFile.setOnClickListener(v -> pickFile());
            btnHide.setOnClickListener(v -> hideData());
            btnExtract.setOnClickListener(v -> extractData());
            btnSaveOutput.setOnClickListener(v -> saveOutput());
            btnAbout.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            });

            updateButtons();
            DebugLogger.log(TAG, "Views initialized successfully");
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-002", "Failed to initialize views", e, this);
        }
    }

    private void initPickers() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    loadImage(uri);
                }
            }
        );

        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    loadFile(uri);
                }
            }
        );
    }

    private void pickImage() {
        imagePickerLauncher.launch("image/*");
    }

    private void pickFile() {
        filePickerLauncher.launch("*/*");
    }

    private void loadImage(Uri uri) {
        try {
            setLoading(true);
            InputStream is = getContentResolver().openInputStream(uri);
            currentImage = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (currentImage != null) {
                ivPreview.setImageBitmap(currentImage);
                outputImage = null;
                extractedData = null;
                tvStatus.setText("Image loaded: " + currentImage.getWidth() + "x" + currentImage.getHeight() +
                    "\nMax capacity: ~" + (currentImage.getWidth() * currentImage.getHeight() * 3 / 8 / 1024) + " KB");
                DebugLogger.log(TAG, "Image loaded: " + currentImage.getWidth() + "x" + currentImage.getHeight());
            } else {
                tvStatus.setText("Failed to load image");
            }
            updateButtons();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-003", "Failed to load image", e, this);
            tvStatus.setText("Error loading image: " + e.getMessage());
        }
        setLoading(false);
    }

    private void loadFile(Uri uri) {
        try {
            setLoading(true);
            currentFileName = getFileName(uri);
            InputStream is = getContentResolver().openInputStream(uri);
            currentFileData = readAllBytes(is);
            if (is != null) is.close();

            if (currentFileData != null) {
                tvStatus.setText("File loaded: " + currentFileName + " (" + formatSize(currentFileData.length) + ")");
                DebugLogger.log(TAG, "File loaded: " + currentFileName + " (" + currentFileData.length + " bytes)");
            } else {
                tvStatus.setText("Failed to load file");
            }
            updateButtons();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-004", "Failed to load file", e, this);
            tvStatus.setText("Error loading file: " + e.getMessage());
        }
        setLoading(false);
    }

    private void hideData() {
        try {
            if (currentImage == null) {
                Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentFileData == null) {
                Toast.makeText(this, "Select a file to hide first", Toast.LENGTH_SHORT).show();
                return;
            }

            String password = etPassword.getText().toString().trim();
            setLoading(true);
            DebugLogger.log(TAG, "Hiding data in image, password: " + (!password.isEmpty() ? "set" : "none"));

            StegoEngine engine = new StegoEngine();
            outputImage = engine.hideData(currentImage, currentFileData, currentFileName, password);

            if (outputImage != null) {
                ivPreview.setImageBitmap(outputImage);
                tvStatus.setText("Data hidden successfully!\nFile: " + currentFileName +
                    " (" + formatSize(currentFileData.length) + ")\nTap Save to export the image.");
                DebugLogger.log(TAG, "Data hidden successfully");
            } else {
                tvStatus.setText("Failed to hide data. File may be too large for this image.");
            }
            updateButtons();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-005", "Failed to hide data", e, this);
            tvStatus.setText("Error: " + e.getMessage());
        }
        setLoading(false);
    }

    private void extractData() {
        try {
            if (currentImage == null) {
                Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show();
                return;
            }

            String password = etPassword.getText().toString().trim();
            setLoading(true);
            DebugLogger.log(TAG, "Extracting data from image, password: " + (!password.isEmpty() ? "set" : "none"));

            StegoEngine engine = new StegoEngine();
            StegoEngine.ExtractedData result = engine.extractData(currentImage, password);

            if (result != null && result.data != null) {
                extractedData = result.data;
                extractedFileName = result.fileName;
                tvStatus.setText("Data extracted!\nFile: " + result.fileName +
                    " (" + formatSize(result.data.length) + ")\nTap Save to export the file.");
                DebugLogger.log(TAG, "Data extracted: " + result.fileName + " (" + result.data.length + " bytes)");
            } else {
                tvStatus.setText("No hidden data found or wrong password.");
            }
            updateButtons();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-006", "Failed to extract data", e, this);
            tvStatus.setText("Error: " + e.getMessage());
        }
        setLoading(false);
    }

    private void saveOutput() {
        try {
            if (outputImage != null) {
                saveImage();
            } else if (extractedData != null) {
                saveFile();
            } else {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-007", "Failed to save output", e, this);
        }
    }

    private void saveImage() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String name = "stego_" + System.currentTimeMillis() + ".png";
            File outFile = new File(downloadsDir, name);

            FileOutputStream fos = new FileOutputStream(outFile);
            outputImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            Toast.makeText(this, "Saved: " + name + "\nTo Downloads folder", Toast.LENGTH_LONG).show();
            DebugLogger.log(TAG, "Image saved: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-008", "Failed to save image", e, this);
        }
    }

    private void saveFile() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String name = extractedFileName != null ? extractedFileName : "extracted_" + System.currentTimeMillis();
            File outFile = new File(downloadsDir, name);

            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(extractedData);
            fos.close();

            Toast.makeText(this, "Saved: " + name + "\nTo Downloads folder", Toast.LENGTH_LONG).show();
            DebugLogger.log(TAG, "File saved: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-009", "Failed to save file", e, this);
        }
    }

    private void updateButtons() {
        btnHide.setEnabled(currentImage != null && currentFileData != null);
        btnExtract.setEnabled(currentImage != null);
        btnSaveOutput.setEnabled(outputImage != null || extractedData != null);
    }

    private String getFileName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        if (name == null || name.isEmpty()) {
            name = "file_" + System.currentTimeMillis();
        }
        return name;
    }

    private byte[] readAllBytes(InputStream is) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-MAIN-010", "Failed to read file bytes", e, this);
            return null;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
