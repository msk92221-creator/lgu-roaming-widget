package com.msk.roamingwidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

final class WidgetBarRenderer {
    private static final int WIDTH = 768;
    private static final int HEIGHT = 32;

    private WidgetBarRenderer() {
    }

    static Bitmap render(double remainingFraction) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = HEIGHT / 2f;
        paint.setColor(Color.rgb(237, 234, 242));
        canvas.drawRoundRect(new RectF(0, 0, WIDTH, HEIGHT), radius, radius, paint);
        if (remainingFraction > 0 && Double.isFinite(remainingFraction)) {
            float fill = (float) (WIDTH * Math.min(1, remainingFraction));
            paint.setShader(new LinearGradient(0, 0, fill, 0,
                    Color.rgb(238, 55, 157), Color.rgb(102, 0, 177), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(0, 0, fill, HEIGHT), radius, radius, paint);
        }
        return bitmap;
    }
}
