package com.msk.roamingwidget;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class UplusAccessibilityService extends AccessibilityService {
    private static final long DEBOUNCE_MS = 600L;
    private static final long STABILITY_MS = 650L;
    private static final long FAILURE_TOAST_INTERVAL_MS = 10_000L;
    private static final int MAX_NODES = 700;
    private static final int MAX_TEXT_LENGTH = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanRunnable = this::scanVisibleUplusWindow;
    private String pendingSignature = "";
    private long pendingSince;
    private long lastFailureToastAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !UplusLauncher.UPLUS_PACKAGE.contentEquals(event.getPackageName())
                || !OfficialUsageStore.isSyncSessionActive(this)) {
            return;
        }
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(scanRunnable);
        resetPending();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(scanRunnable);
        super.onDestroy();
    }

    private void scanVisibleUplusWindow() {
        if (!OfficialUsageStore.isSyncSessionActive(this)) {
            resetPending();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !UplusLauncher.UPLUS_PACKAGE.contentEquals(root.getPackageName())) {
            return;
        }

        UplusScreenParser.Result result = UplusScreenParser.parse(collectVisibleText(root));
        if (result.status == UplusScreenParser.Status.SUCCESS) {
            handleStableSuccess(result);
            return;
        }
        resetPending();
        OfficialUsageStore.saveScanResult(this, result);
        if (result.status == UplusScreenParser.Status.NO_VALUE
                || result.status == UplusScreenParser.Status.AMBIGUOUS) {
            maybeShowFailureToast();
        }
    }

    private void handleStableSuccess(UplusScreenParser.Result result) {
        String signature = result.kind.name() + "|" + result.primaryText + "|" + result.detailsText;
        long now = System.currentTimeMillis();
        if (signature.equals(pendingSignature) && now - pendingSince >= STABILITY_MS) {
            OfficialUsageStore.saveSuccess(this, result);
            WidgetUpdater.updateAll(this);
            resetPending();
            Toast.makeText(this, R.string.toast_capture_success, Toast.LENGTH_LONG).show();
            return;
        }
        pendingSignature = signature;
        pendingSince = now;
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, STABILITY_MS);
    }

    private void maybeShowFailureToast() {
        long now = System.currentTimeMillis();
        if (now - lastFailureToastAt < FAILURE_TOAST_INTERVAL_MS) {
            return;
        }
        lastFailureToastAt = now;
        Toast.makeText(this, R.string.toast_screen_no_value, Toast.LENGTH_LONG).show();
    }

    private void resetPending() {
        pendingSignature = "";
        pendingSince = 0L;
    }

    private static List<String> collectVisibleText(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        List<String> output = new ArrayList<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            visited++;
            if (node.isVisibleToUser() && !node.isPassword()) {
                addText(output, node.getText());
                addText(output, node.getContentDescription());
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

    private static void addText(List<String> output, CharSequence value) {
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        output.add(text);
    }
}
