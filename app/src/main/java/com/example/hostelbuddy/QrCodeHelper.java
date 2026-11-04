package com.example.hostelbuddy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * QR Code generation helper using ZXing.
 * Generates an opaque, clean QR code for laundry drop-off requests without personal information.
 */
public final class QrCodeHelper {

    private static final int COLOR_DARK = Color.parseColor("#7A0019"); // NMIMS Burgundy
    private static final int COLOR_LIGHT = Color.WHITE;

    private QrCodeHelper() { }

    /**
     * Generates a high-contrast QR code bitmap for an opaque token or request ID.
     */
    public static Bitmap generateQrBitmap(String content, int width, int height) {
        if (content == null || content.trim().isEmpty()) {
            content = "EMPTY";
        }

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

            BitMatrix bitMatrix = writer.encode(content.trim(), BarcodeFormat.QR_CODE, width, height, hints);
            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            int[] pixels = new int[matrixWidth * matrixHeight];

            for (int y = 0; y < matrixHeight; y++) {
                int offset = y * matrixWidth;
                for (int x = 0; x < matrixWidth; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? COLOR_DARK : COLOR_LIGHT;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
            return bitmap;
        } catch (Exception e) {
            return generateFallbackBitmap(content, width, height);
        }
    }

    /**
     * Fallback graphic generator if ZXing throws an unexpected exception.
     */
    private static Bitmap generateFallbackBitmap(String text, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint();
        paint.setColor(COLOR_DARK);
        paint.setStyle(Paint.Style.FILL);

        int hash = Math.abs(text.hashCode());
        int gridSize = 12;
        int cellW = width / gridSize;
        int cellH = height / gridSize;

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                boolean isCorner = (r < 3 && c < 3) || (r < 3 && c >= gridSize - 3) || (r >= gridSize - 3 && c < 3);
                if (isCorner) {
                    canvas.drawRect(c * cellW, r * cellH, (c + 1) * cellW - 1, (r + 1) * cellH - 1, paint);
                } else if (((hash >> ((r * gridSize + c) % 31)) & 1) == 1) {
                    canvas.drawRect(c * cellW + 1, r * cellH + 1, (c + 1) * cellW - 2, (r + 1) * cellH - 2, paint);
                }
            }
        }
        return bitmap;
    }
}
