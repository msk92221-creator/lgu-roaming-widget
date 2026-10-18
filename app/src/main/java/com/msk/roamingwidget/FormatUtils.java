package com.msk.roamingwidget;

import android.content.Context;
import android.text.format.DateUtils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class FormatUtils {
    static final long DECIMAL_GB = 1_000_000_000L;
    private static final long DECIMAL_MB = 1_000_000L;

    private FormatUtils() {
    }

    static long gbToBytes(double gigabytes) {
        return Math.round(gigabytes * DECIMAL_GB);
    }

    static double bytesToGb(long bytes) {
        return (double) bytes / DECIMAL_GB;
    }

    static String formatBytes(long bytes) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.KOREA);
        if (bytes >= DECIMAL_GB) {
            DecimalFormat formatter = new DecimalFormat(bytes >= 10L * DECIMAL_GB ? "0.#" : "0.##", symbols);
            return formatter.format(bytesToGb(bytes)) + " GB";
        }
        DecimalFormat formatter = new DecimalFormat("0.#", symbols);
        return formatter.format((double) bytes / DECIMAL_MB) + " MB";
    }

    static String formatInputGb(long bytes) {
        DecimalFormat formatter = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.KOREA));
        return formatter.format(bytesToGb(bytes));
    }

    static String relativeTime(Context context, long timestamp) {
        if (timestamp <= 0L) {
            return "아직 갱신 안 됨";
        }
        return DateUtils.getRelativeTimeSpanString(
                timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString();
    }

    static String absoluteTime(long timestamp) {
        if (timestamp <= 0L) {
            return "-";
        }
        return new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(new Date(timestamp));
    }
}
