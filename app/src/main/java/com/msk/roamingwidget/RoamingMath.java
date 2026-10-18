package com.msk.roamingwidget;

final class RoamingMath {
    private RoamingMath() {
    }

    static long calculateRemaining(long anchorRemaining, long baselineCounter, long currentCounter) {
        long delta = Math.max(0L, currentCounter - baselineCounter);
        return Math.max(0L, anchorRemaining - delta);
    }

    static long usedBytes(long totalBytes, long remainingBytes) {
        return Math.max(0L, totalBytes - remainingBytes);
    }

    static int remainingPermille(long totalBytes, long remainingBytes) {
        if (totalBytes <= 0L) {
            return 0;
        }
        double ratio = (double) remainingBytes / (double) totalBytes;
        return (int) Math.round(Math.max(0.0d, Math.min(1.0d, ratio)) * 1000.0d);
    }
}
