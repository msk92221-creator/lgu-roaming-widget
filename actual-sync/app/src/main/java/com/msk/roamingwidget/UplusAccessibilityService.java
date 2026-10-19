package com.msk.roamingwidget;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;

public final class UplusAccessibilityService extends AccessibilityService {
    private static final long DEBOUNCE_MS = 600L;
    private static final long SCAN_INTERVAL_MS = 1_000L;
    private static final long FAILURE_TOAST_INTERVAL_MS = 10_000L;
    private static final int MAX_NODES = 700;
    private static final int MAX_TEXT_LENGTH = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<UplusAccessibilityService> connected = new WeakReference<>(null);
    private final Runnable scanRunnable = this::runScan;
    private boolean scanQueued;
    private long requestTimestamp;
    private RefreshCaptureWindow captureWindow = new RefreshCaptureWindow();
    private boolean successNotified;
    private String lastFailureKey = "";
    private long lastFailureToastAt;

    static void requestScan() {
        UplusAccessibilityService service = connected.get();
        if (service != null) {
            service.handler.post(() -> {
                service.handler.removeCallbacks(service.scanRunnable);
                service.scanQueued = false;
                service.scheduleScan(0);
            });
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = new WeakReference<>(this);
        scheduleScan(0);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !UplusLauncher.UPLUS_PACKAGE.contentEquals(event.getPackageName())
                || !OfficialUsageStore.isSyncSessionActive(this)) {
            return;
        }
        scheduleScan(DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(scanRunnable);
        scanQueued = false;
        resetRequest();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (connected.get() == this) {
            connected.clear();
        }
        super.onDestroy();
    }

    private void scheduleScan(long delay) {
        if (!scanQueued && OfficialUsageStore.isSyncSessionActive(this)) {
            scanQueued = true;
            handler.postDelayed(scanRunnable, delay);
        }
    }

    private void runScan() {
        scanQueued = false;
        if (!OfficialUsageStore.isSyncSessionActive(this)) {
            if (requestTimestamp != 0) {
                resetRequest();
                WidgetUpdater.updateAll(this);
            }
            return;
        }
        long currentRequest = OfficialUsageStore.syncRequestTimestamp(this);
        if (currentRequest != requestTimestamp) {
            resetRequest();
            requestTimestamp = currentRequest;
        }
        if (captureWindow.isComplete(SystemClock.elapsedRealtime())) {
            OfficialUsageStore.finishSync(this);
            resetRequest();
            WidgetUpdater.updateAll(this);
            return;
        }
        scanVisibleUplusWindow();
        scheduleScan(SCAN_INTERVAL_MS);
    }

    private void scanVisibleUplusWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !UplusLauncher.UPLUS_PACKAGE.contentEquals(root.getPackageName())) {
            captureWindow.resetCandidate();
            return;
        }

        UplusScreenParser.Result result = UplusScreenParser.parseScreen(collectVisibleText(root));
        if (result.status == UplusScreenParser.Status.SUCCESS) {
            handleStableSuccess(result);
            return;
        }
        captureWindow.resetCandidate();
        String failureKey = result.status.name() + "|" + result.diagnostic;
        if (!failureKey.equals(lastFailureKey)) {
            OfficialUsageStore.saveScanResult(this, result);
            WidgetUpdater.updateAll(this);
            lastFailureKey = failureKey;
        }
        if (result.status == UplusScreenParser.Status.NO_VALUE
                || result.status == UplusScreenParser.Status.AMBIGUOUS) {
            maybeShowFailureToast();
        }
    }

    private void handleStableSuccess(UplusScreenParser.Result result) {
        String signature = result.kind.name() + "|" + result.primaryText + "|" + result.detailsText;
        long now = SystemClock.elapsedRealtime();
        if (captureWindow.observe(signature, now)) {
            OfficialUsageStore.saveSuccess(this, result);
            WidgetUpdater.updateAll(this);
            lastFailureKey = "";
            if (!successNotified) {
                Toast.makeText(this, R.string.toast_capture_success, Toast.LENGTH_LONG).show();
                successNotified = true;
            }
        } else if (!lastFailureKey.isEmpty() && captureWindow.hasStableSavedValue(signature, now)) {
            OfficialUsageStore.restoreReadyStatus(this, result);
            lastFailureKey = "";
            WidgetUpdater.updateAll(this);
        }
    }

    private void maybeShowFailureToast() {
        long now = System.currentTimeMillis();
        if (now - lastFailureToastAt < FAILURE_TOAST_INTERVAL_MS) {
            return;
        }
        lastFailureToastAt = now;
        Toast.makeText(this, R.string.toast_screen_no_value, Toast.LENGTH_LONG).show();
    }

    private void resetRequest() {
        captureWindow = new RefreshCaptureWindow();
        requestTimestamp = 0;
        successNotified = false;
        lastFailureKey = "";
    }

    private static List<ScreenText> collectVisibleText(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        List<ScreenText> output = new ArrayList<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;
            if (node.isVisibleToUser() && !node.isPassword()) {
                List<String> texts = new ArrayList<>(1);
                addNodeText(texts, node.getText(), node.getContentDescription());
                if (!texts.isEmpty()) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    output.add(new ScreenText(texts.get(0), bounds.left, bounds.top,
                            bounds.right, bounds.bottom));
                }
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) {
                    queue.addLast(child);
                }
            }
        }
        return output;
    }

    static void addNodeText(
            List<String> output,
            CharSequence textValue,
            CharSequence descriptionValue
    ) {
        String text = sanitizeText(textValue);
        String description = sanitizeText(descriptionValue);
        if (text.isEmpty() && description.isEmpty()) {
            return;
        }
        if (text.isEmpty()) {
            output.add(description);
        } else if (description.isEmpty() || text.equals(description)) {
            output.add(text);
        } else {
            output.add(text + " " + description);
        }
    }

    private static String sanitizeText(CharSequence value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }
}
