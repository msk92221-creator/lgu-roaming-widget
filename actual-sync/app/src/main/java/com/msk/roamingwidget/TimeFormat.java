package com.msk.roamingwidget;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class TimeFormat {
    private TimeFormat() {
    }

    static String relative(Context context, long timestamp) {
        if (timestamp <= 0L) {
            return "아직 확인 안 됨";
        }
        return new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
                .format(new Date(timestamp));
    }
}
