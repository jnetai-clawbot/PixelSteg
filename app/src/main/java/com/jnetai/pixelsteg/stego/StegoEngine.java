package com.jnetai.pixelsteg.stego;

import android.graphics.Bitmap;
import com.jnetai.pixelsteg.utils.ErrorHandler;
import com.jnetai.pixelsteg.utils.DebugLogger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class StegoEngine {

    private static final String TAG = "StegoEngine";
    private static final int HEADER_SIZE = 260;
    private static final String MAGIC = "STEG";

    public Bitmap hideData(Bitmap image, byte[] data, String fileName, String password) {
        try {
            DebugLogger.log(TAG, "Hiding data: " + fileName + " (" + data.length + " bytes)");

            int width = image.getWidth();
            int height = image.getHeight();
            int maxBytes = (width * height * 3) / 8 - HEADER_SIZE;

            if (data.length > maxBytes) {
                DebugLogger.log(TAG, "Data too large: " + data.length + " > " + maxBytes);
                return null;
            }

            byte[] dataToHide = data;
            if (password != null && !password.isEmpty()) {
                dataToHide = encrypt(data, password);
                DebugLogger.log(TAG, "Data encrypted with password");
            }

            byte[] fileNameBytes = fileName != null ? fileName.getBytes(StandardCharsets.UTF_8) : new byte[0];
            byte[] header = buildHeader(fileNameBytes, dataToHide.length);
            byte[] payload = new byte[header.length + dataToHide.length];
            System.arraycopy(header, 0, payload, 0, header.length);
            System.arraycopy(dataToHide, 0, payload, header.length, dataToHide.length);

            Bitmap result = image.copy(Bitmap.Config.ARGB_8888, true);
            int[] pixels = new int[width * height];
            result.getPixels(pixels, 0, width, 0, 0, width, height);

            int bitIndex = 0;
            for (int i = 0; i < pixels.length && bitIndex < payload.length * 8; i++) {
                int pixel = pixels[i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                if (bitIndex < payload.length * 8) {
                    r = (r & 0xFE) | getBit(payload, bitIndex++);
                }
                if (bitIndex < payload.length * 8) {
                    g = (g & 0xFE) | getBit(payload, bitIndex++);
                }
                if (bitIndex < payload.length * 8) {
                    b = (b & 0xFE) | getBit(payload, bitIndex++);
                }

                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            result.setPixels(pixels, 0, width, 0, 0, width, height);
            DebugLogger.log(TAG, "Data hidden successfully, payload: " + payload.length + " bytes");
            return result;

        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-STEG-001", "Failed to hide data", e, null);
            return null;
        }
    }

    public ExtractedData extractData(Bitmap image, String password) {
        try {
            DebugLogger.log(TAG, "Extracting data from image");

            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixels(pixels, 0, width, 0, 0, width, height);

            byte[] headerBytes = new byte[HEADER_SIZE];
            int bitIndex = 0;
            for (int i = 0; i < pixels.length && bitIndex < HEADER_SIZE * 8; i++) {
                int pixel = pixels[i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                if (bitIndex < HEADER_SIZE * 8) setBit(headerBytes, bitIndex++, r & 1);
                if (bitIndex < HEADER_SIZE * 8) setBit(headerBytes, bitIndex++, g & 1);
                if (bitIndex < HEADER_SIZE * 8) setBit(headerBytes, bitIndex++, b & 1);
            }

            String magic = new String(Arrays.copyOfRange(headerBytes, 0, 4), StandardCharsets.UTF_8);
            if (!MAGIC.equals(magic)) {
                DebugLogger.log(TAG, "No stego data found (magic mismatch)");
                return null;
            }

            int fileNameLength = ((headerBytes[4] & 0xFF) << 8) | (headerBytes[5] & 0xFF);
            String fileName = new String(Arrays.copyOfRange(headerBytes, 6, 6 + fileNameLength), StandardCharsets.UTF_8);

            int dataLength = ((headerBytes[256] & 0xFF) << 24) |
                            ((headerBytes[257] & 0xFF) << 16) |
                            ((headerBytes[258] & 0xFF) << 8) |
                            (headerBytes[259] & 0xFF);

            DebugLogger.log(TAG, "Header: fileName=" + fileName + ", dataLength=" + dataLength);

            byte[] data = new byte[dataLength];
            for (int i = 0; i < dataLength * 8; i++) {
                int pixelIdx = (HEADER_SIZE * 8 + i) / 3;
                int colorChannel = (HEADER_SIZE * 8 + i) % 3;
                if (pixelIdx < pixels.length) {
                    int pixel = pixels[pixelIdx];
                    int bit;
                    if (colorChannel == 0) bit = (pixel >> 16) & 1;
                    else if (colorChannel == 1) bit = (pixel >> 8) & 1;
                    else bit = pixel & 1;
                    setBit(data, i, bit);
                }
            }

            if (password != null && !password.isEmpty()) {
                data = decrypt(data, password);
                if (data == null) {
                    DebugLogger.log(TAG, "Decryption failed - wrong password?");
                    return null;
                }
                DebugLogger.log(TAG, "Data decrypted successfully");
            }

            ExtractedData result = new ExtractedData();
            result.fileName = fileName;
            result.data = data;
            DebugLogger.log(TAG, "Data extracted: " + fileName + " (" + data.length + " bytes)");
            return result;

        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-STEG-002", "Failed to extract data", e, null);
            return null;
        }
    }

    private byte[] buildHeader(byte[] fileNameBytes, int dataLength) {
        byte[] header = new byte[HEADER_SIZE];
        System.arraycopy(MAGIC.getBytes(StandardCharsets.UTF_8), 0, header, 0, 4);

        int nameLen = Math.min(fileNameBytes.length, 250);
        header[4] = (byte) ((nameLen >> 8) & 0xFF);
        header[5] = (byte) (nameLen & 0xFF);
        System.arraycopy(fileNameBytes, 0, header, 6, nameLen);

        header[256] = (byte) ((dataLength >> 24) & 0xFF);
        header[257] = (byte) ((dataLength >> 16) & 0xFF);
        header[258] = (byte) ((dataLength >> 8) & 0xFF);
        header[259] = (byte) (dataLength & 0xFF);

        return header;
    }

    private int getBit(byte[] data, int index) {
        int byteIndex = index / 8;
        int bitOffset = 7 - (index % 8);
        return (data[byteIndex] >> bitOffset) & 1;
    }

    private void setBit(byte[] data, int index, int bit) {
        int byteIndex = index / 8;
        int bitOffset = 7 - (index % 8);
        if (bit == 1) {
            data[byteIndex] |= (1 << bitOffset);
        } else {
            data[byteIndex] &= ~(1 << bitOffset);
        }
    }

    private byte[] encrypt(byte[] data, String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = Arrays.copyOf(sha.digest(password.getBytes(StandardCharsets.UTF_8)), 16);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-STEG-003", "Encryption failed", e, null);
            return data;
        }
    }

    private byte[] decrypt(byte[] data, String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = Arrays.copyOf(sha.digest(password.getBytes(StandardCharsets.UTF_8)), 16);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            ErrorHandler.handle(TAG, "ERR-STEG-004", "Decryption failed", e, null);
            return null;
        }
    }

    public static class ExtractedData {
        public String fileName;
        public byte[] data;
    }
}
