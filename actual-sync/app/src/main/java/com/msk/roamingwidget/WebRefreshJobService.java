package com.msk.roamingwidget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;

/** Loads the official website without displaying a window or opening U+one. */
public final class WebRefreshJobService extends JobService {
    private static final long TIMEOUT_MS = 45_000L;
    private static final long POLL_MS = 750L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CaptureStability stability = new CaptureStability();
    private final Runnable poll = this::readPage;
    private final Runnable timeout = this::onTimeout;

    private JobParameters activeJob;
    private WebView webView;
    private OfficialWebPage.Client client;
    private long requestToken;
    private boolean pageFinished;
    private boolean reading;
    private boolean finished = true;
    private long navigationGeneration;
    private int readCount;
    private UplusScreenParser.Result lastObservation;
    private boolean lastFresh;
    private String lastRecordedKey = "";

    @Override
    public boolean onStartJob(JobParameters parameters) {
        long token = parameters.getExtras().getLong(BackgroundRefresh.EXTRA_REQUEST_TOKEN, 0L);
        if (!OfficialUsageStore.isCurrentRequest(this, token)) {
            WidgetUpdater.updateAll(this);
            return false;
        }
        if (!finished) {
            if (token == requestToken) {
                return true;
            }
            finish(UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                    "새 조회 요청으로 이전 조회 종료 · 이전 값 유지"), true);
        }

        activeJob = parameters;
        requestToken = token;
        finished = false;
        pageFinished = false;
        reading = false;
        readCount = 0;
        lastFresh = false;
        lastRecordedKey = "";
        lastObservation = UplusScreenParser.Result.status(UplusScreenParser.Status.LOADING,
                "공식 웹 페이지 불러오기 시작 · 수량 영역 확인 대기");
        stability.reset();
        main.postDelayed(timeout, TIMEOUT_MS);
        try {
            webView = OfficialWebPage.create(this);
            webView.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
            webView.layout(0, 0, 1080, 1920);
            client = new OfficialWebPage.Client(false,
                    () -> onPageFinished(token),
                    result -> main.post(() -> onPageFailure(token, result)),
                    url -> onNavigation(token));
            webView.setWebViewClient(client);
            webView.loadUrl(OfficialWebPage.URL);
            return true;
        } catch (RuntimeException ignored) {
            finish(UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                    "공식 웹 조회를 시작하지 못함 · 이전 값과 확인 시각 유지"), false);
            return false;
        }
    }

    private void onNavigation(long token) {
        if (!isActive(token)) return;
        navigationGeneration++;
        pageFinished = false;
        stability.reset();
        lastObservation = UplusScreenParser.Result.status(UplusScreenParser.Status.LOADING,
                "공식 웹 페이지 이동 중 · 수량 영역 확인 대기");
        lastFresh = false;
        lastRecordedKey = "";
    }

    private void onPageFinished(long token) {
        if (!isActive(token)) {
            return;
        }
        pageFinished = true;
        stability.reset();
        schedulePoll(0L);
    }

    private void onPageFailure(long token, UplusScreenParser.Result result) {
        if (!isActive(token)) {
            return;
        }
        if (result == null || result.status == UplusScreenParser.Status.LOADING) {
            return;
        }
        finish(result.status == UplusScreenParser.Status.SUCCESS
                ? UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                        "공식 웹 조회 상태를 확인하지 못함 · 이전 값 유지")
                : result, true);
    }

    private void schedulePoll(long delay) {
        main.removeCallbacks(poll);
        if (!finished) {
            main.postDelayed(poll, delay);
        }
    }

    private void readPage() {
        long token = requestToken;
        if (!isActive(token)) {
            if (!finished) {
                finish(null, true);
            }
            return;
        }
        if (reading || webView == null) {
            return;
        }
        reading = true;
        WebView expectedView = webView;
        long expectedNavigation = navigationGeneration;
        try {
            OfficialWebPage.read(expectedView, result -> main.post(() -> {
                if (!isActive(token) || webView != expectedView) {
                    return;
                }
                reading = false;
                if (expectedNavigation != navigationGeneration) {
                    if (pageFinished) schedulePoll(POLL_MS);
                    return;
                }
                if (result != null && result.status == UplusScreenParser.Status.LOGIN_REQUIRED) {
                    finish(result, true);
                    return;
                }
                boolean fresh = pageFinished && client != null && client.hasFreshBalanceRequest();
                recordObservation(result, fresh);
                if (stability.observe(result, fresh)) {
                    finish(result, true);
                    return;
                }
                schedulePoll(POLL_MS);
            }));
        } catch (RuntimeException ignored) {
            reading = false;
            finish(UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                    "공식 웹 데이터를 읽지 못함 · 이전 값과 확인 시각 유지"), true);
        }
    }

    private void recordObservation(UplusScreenParser.Result result, boolean fresh) {
        readCount++;
        lastFresh = fresh;
        if (result == null) {
            result = OfficialWebPage.unavailable("공식 웹 읽기 결과 없음");
        }
        UplusScreenParser.Status status = result.status == UplusScreenParser.Status.SUCCESS
                ? UplusScreenParser.Status.LOADING : result.status;
        String reason = result.status == UplusScreenParser.Status.SUCCESS && !fresh
                ? "공식 웹 수량 인식됨 · 새 로밍 조회 요청을 아직 확인하지 못함"
                : safeDiagnostic(result.diagnostic);
        lastObservation = UplusScreenParser.Result.status(status, reason);
        String key = status.name() + "|" + reason + "|" + fresh;
        if (!key.equals(lastRecordedKey)
                && OfficialUsageStore.isCurrentRequest(this, requestToken)) {
            OfficialUsageStore.saveScanResult(this, UplusScreenParser.Result.status(status,
                    diagnosticSnapshot("공식 웹 조회 진행 중")));
            lastRecordedKey = key;
        }
    }

    private void onTimeout() {
        UplusScreenParser.Status status = lastObservation != null
                && lastObservation.status == UplusScreenParser.Status.AMBIGUOUS
                ? UplusScreenParser.Status.AMBIGUOUS : UplusScreenParser.Status.NO_VALUE;
        finish(UplusScreenParser.Result.status(status,
                diagnosticSnapshot("공식 웹 조회 제한시간 초과 · 이전 값과 확인 시각 유지")), true);
    }

    private String diagnosticSnapshot(String reason) {
        return reason + "\n"
                + (lastObservation == null ? "수량 읽기 결과 없음"
                : safeDiagnostic(lastObservation.diagnostic) + "\n읽기 상태 "
                + lastObservation.status.name())
                + " · 조회 요청 " + (lastFresh ? "관찰됨" : "미관찰")
                + " · 문서 완료 " + (pageFinished ? "확인됨" : "대기")
                + " · 확인 " + readCount + "회";
    }

    private static String safeDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.isEmpty()) return "공식 웹 수량 영역 확인 대기";
        return diagnostic.length() > 4096 ? diagnostic.substring(0, 4096) : diagnostic;
    }

    private boolean isActive(long token) {
        return !finished && activeJob != null && token == requestToken
                && OfficialUsageStore.isCurrentRequest(this, token);
    }

    private void finish(UplusScreenParser.Result result, boolean notifyScheduler) {
        if (finished) {
            return;
        }
        finished = true;
        JobParameters completedJob = activeJob;
        long completedToken = requestToken;
        activeJob = null;
        pageFinished = false;
        reading = false;
        main.removeCallbacksAndMessages(null);
        stability.reset();
        try {
            CookieManager.getInstance().flush();
        } catch (RuntimeException ignored) {
            // Cookie values stay inside WebView even if persistence is unavailable.
        }
        destroyWebView();
        if (result != null && OfficialUsageStore.isCurrentRequest(this, completedToken)) {
            OfficialUsageStore.completeWeb(this, completedToken, result);
            WidgetUpdater.updateAll(this);
        }
        if (notifyScheduler && completedJob != null) {
            jobFinished(completedJob, false);
        }
    }

    private void destroyWebView() {
        WebView oldView = webView;
        webView = null;
        client = null;
        if (oldView == null) {
            return;
        }
        try {
            oldView.stopLoading();
        } catch (RuntimeException ignored) {
            // Still destroy a WebView whose renderer has already stopped.
        }
        try {
            oldView.destroy();
        } catch (RuntimeException ignored) {
            // No UI, headers, cookies, or raw page data are retained here.
        }
    }

    @Override
    public boolean onStopJob(JobParameters parameters) {
        long token = parameters.getExtras().getLong(BackgroundRefresh.EXTRA_REQUEST_TOKEN, 0L);
        if (!finished && token == requestToken) {
            finish(UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                    "백그라운드 조회가 중단됨 · 이전 값과 확인 시각 유지"), false);
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (!finished) {
            finish(UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE,
                    "백그라운드 조회가 종료됨 · 이전 값과 확인 시각 유지"), false);
        } else {
            main.removeCallbacksAndMessages(null);
            destroyWebView();
        }
        super.onDestroy();
    }

    /** Pure policy: a fresh request and two consecutive identical aggregate reads are required. */
    static final class CaptureStability {
        private String previousSignature;

        boolean observe(UplusScreenParser.Result result, boolean fresh) {
            if (!fresh || result == null || result.status != UplusScreenParser.Status.SUCCESS
                    || result.balance == null) {
                reset();
                return false;
            }
            String signature = result.balance.signature();
            boolean stable = signature.equals(previousSignature);
            previousSignature = signature;
            return stable;
        }

        void reset() {
            previousSignature = null;
        }
    }
}
