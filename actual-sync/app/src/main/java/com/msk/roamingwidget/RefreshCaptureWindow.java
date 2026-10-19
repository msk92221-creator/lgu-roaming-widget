package com.msk.roamingwidget;

/** A request observes late page updates briefly, without re-saving unchanged values. */
final class RefreshCaptureWindow {
    private static final long STABILITY_MS = 650;
    private static final long FOLLOW_UP_MS = 10_000;
    private String candidate = "";
    private long firstSeenAt;
    private String saved = "";
    private long firstSavedAt = -1;

    boolean observe(String signature, long now) {
        if (isComplete(now)) {
            return false;
        }
        if (!signature.equals(candidate) || now < firstSeenAt) {
            candidate = signature;
            firstSeenAt = now;
            return false;
        }
        if (now - firstSeenAt < STABILITY_MS || signature.equals(saved)) {
            return false;
        }
        saved = signature;
        if (firstSavedAt < 0) {
            firstSavedAt = now;
        }
        return true;
    }

    boolean isComplete(long now) {
        return firstSavedAt >= 0 && now - firstSavedAt >= FOLLOW_UP_MS;
    }

    boolean hasStableSavedValue(String signature, long now) {
        return signature.equals(saved) && signature.equals(candidate)
                && now >= firstSeenAt && now - firstSeenAt >= STABILITY_MS;
    }

    void resetCandidate() {
        candidate = "";
        firstSeenAt = 0;
    }
}
