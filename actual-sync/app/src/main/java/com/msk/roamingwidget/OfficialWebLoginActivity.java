package com.msk.roamingwidget;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;

/** User-opened onboarding only. Widget refresh never launches this or any other Activity. */
public final class OfficialWebLoginActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private OfficialWebPage.Client client;
    private TextView message;
    private TextView origin;
    private long request;
    private boolean finished;
    private boolean resumed;
    private boolean reading;
    private String candidate = "";
    private int stable;
    private long navigationGeneration;
    private int readCount;
    private UplusScreenParser.Result lastObservation;
    private UplusScreenParser.Result terminalFailure;
    private String lastRecordedKey = "";

    private final Runnable poll = this::readPage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        BackgroundRefresh.cancel(this);
        OfficialUsageStore.beginSync(this);
        request = OfficialUsageStore.syncRequestTimestamp(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.surface));
        message = new TextView(this);
        message.setText("공식 LG U+ 웹에서 로그인해 주세요.\n"
                + "로밍 잔여량이 확인되면 연결을 저장합니다. 비밀번호는 이 앱이 읽지 않습니다.");
        message.setTextColor(getColor(R.color.ink));
        message.setPadding(24, 20, 24, 12);
        root.addView(message);

        origin = new TextView(this);
        origin.setTextColor(getColor(R.color.ink));
        origin.setTextSize(14);
        origin.setPadding(24, 8, 24, 8);
        root.addView(origin);
        showOrigin(OfficialWebPage.URL);

        LinearLayout buttons = new LinearLayout(this);
        Button close = new Button(this);
        close.setText("닫기");
        close.setOnClickListener(view -> finish());
        buttons.addView(close, new LinearLayout.LayoutParams(0, -2, 1));
        Button roaming = new Button(this);
        roaming.setText("로밍 조회로 이동");
        roaming.setOnClickListener(view -> {
            candidate = "";
            stable = 0;
            if (webView != null) webView.loadUrl(OfficialWebPage.URL);
        });
        buttons.addView(roaming, new LinearLayout.LayoutParams(0, -2, 2));
        root.addView(buttons);
        webView = OfficialWebPage.create(this);
        client = new OfficialWebPage.Client(true, this::scheduleRead, this::showFailure,
                this::onNavigation);
        webView.setWebViewClient(client);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        applyInsets(root);
        webView.loadUrl(OfficialWebPage.URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (webView != null) {
            webView.onResume();
            scheduleRead();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(poll);
        if (webView != null) {
            CookieManager.getInstance().flush();
            webView.onPause();
        }
        super.onPause();
    }

    private void scheduleRead() {
        handler.removeCallbacks(poll);
        if (!finished && resumed) {
            handler.postDelayed(poll, 900L);
        }
    }

    private void readPage() {
        if (finished || !resumed || reading || webView == null || terminalFailure != null) {
            return;
        }
        reading = true;
        WebView expectedView = webView;
        long expectedNavigation = navigationGeneration;
        try {
            OfficialWebPage.read(expectedView,
                    result -> onPageRead(expectedView, expectedNavigation, result));
        } catch (RuntimeException exception) {
            reading = false;
            showFailure(OfficialWebPage.unavailable("공식 웹 읽기 실행 실패 · 이전 값 유지"));
        }
    }

    private void onPageRead(WebView expectedView, long expectedNavigation,
            UplusScreenParser.Result result) {
        if (finished || webView != expectedView) return;
        reading = false;
        if (!resumed || terminalFailure != null) return;
        if (expectedNavigation != navigationGeneration) {
            scheduleRead();
            return;
        }
        if (result == null) result = OfficialWebPage.unavailable("공식 웹 읽기 결과 없음");
        boolean fresh = client != null && client.hasFreshBalanceRequest();
        recordObservation(result, fresh);
        if (result.status == UplusScreenParser.Status.SUCCESS && result.balance != null && fresh) {
            String signature = result.balance.signature();
            stable = signature.equals(candidate) ? stable + 1 : 1;
            candidate = signature;
            if (stable >= 2) {
                // Authentication remains inside Android WebView. No cookie value is read.
                CookieManager.getInstance().flush();
                if (!OfficialUsageStore.isCurrentRequest(this, request)) {
                    OfficialUsageStore.beginSync(this);
                    request = OfficialUsageStore.syncRequestTimestamp(this);
                }
                OfficialUsageStore.completeWeb(this, request, result);
                WebSessionState.markConfigured(this);
                WidgetUpdater.updateAll(this);
                finished = true;
                Toast.makeText(this, "공식 로밍 데이터 연결 완료 · 이제 위젯에서 새로고침하세요",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } else {
            stable = 0;
            candidate = "";
            if (result.status == UplusScreenParser.Status.LOGIN_REQUIRED) {
                message.setText("공식 LG U+ 화면에서 로그인해 주세요.\n"
                        + "로그인 뒤 홈으로 가면 위의 ‘로밍 조회로 이동’을 누르세요.\n"
                        + visibleDiagnostic(lastObservation.diagnostic));
            } else if (OfficialWebPage.isRoamingUrl(webView.getUrl())) {
                message.setText("공식 로밍 데이터를 확인 중입니다.\n"
                        + visibleDiagnostic(lastObservation.diagnostic));
            }
        }
        scheduleRead();
    }

    private void recordObservation(UplusScreenParser.Result result, boolean fresh) {
        readCount++;
        UplusScreenParser.Status status = result.status == UplusScreenParser.Status.SUCCESS
                ? UplusScreenParser.Status.LOADING : result.status;
        String reason = result.status == UplusScreenParser.Status.SUCCESS && !fresh
                ? "공식 웹 수량 인식됨 · 새 로밍 조회 요청을 아직 확인하지 못함"
                : safeDiagnostic(result.diagnostic);
        String key = status.name() + "|" + reason + "|" + fresh;
        lastObservation = UplusScreenParser.Result.status(status, reason
                + "\n읽기 상태 " + result.status.name()
                + " · 조회 요청 " + (fresh ? "관찰됨" : "미관찰") + " · 확인 " + readCount + "회");
        if (!key.equals(lastRecordedKey) && OfficialUsageStore.isCurrentRequest(this, request)) {
            OfficialUsageStore.saveScanResult(this, lastObservation);
            lastRecordedKey = key;
        }
    }

    private void showFailure(UplusScreenParser.Result result) {
        if (!finished) {
            terminalFailure = result;
            lastObservation = result;
            handler.removeCallbacks(poll);
            OfficialUsageStore.completeWeb(this, request, result);
            message.setText(result.diagnostic + "\nU+ID 로그인 방식으로 다시 시도할 수 있습니다.");
            WidgetUpdater.updateAll(this);
            if (client != null && client.isRendererGone()) {
                resumed = false;
                handler.removeCallbacks(poll);
                destroyWebView();
                Toast.makeText(this, result.diagnostic, Toast.LENGTH_LONG).show();
                handler.post(this::finish);
            }
        }
    }

    private void onNavigation(String url) {
        if (finished || webView == null) return;
        navigationGeneration++;
        candidate = "";
        stable = 0;
        readCount = 0;
        lastRecordedKey = "";
        terminalFailure = null;
        lastObservation = UplusScreenParser.Result.status(UplusScreenParser.Status.LOADING,
                "공식 웹 페이지 이동 중 · 수량 영역 확인 대기");
        if (resumed && !OfficialUsageStore.isCurrentRequest(this, request)) {
            OfficialUsageStore.beginSync(this);
            request = OfficialUsageStore.syncRequestTimestamp(this);
        }
        showOrigin(url);
    }

    private static String safeDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.isEmpty()) return "공식 웹 수량 영역 확인 대기";
        return diagnostic.length() > 4096 ? diagnostic.substring(0, 4096) : diagnostic;
    }

    private static String visibleDiagnostic(String diagnostic) {
        int assets = diagnostic.indexOf("\n공개 스크립트");
        return assets >= 0 ? diagnostic.substring(0, assets)
                + "\n상세 원인은 앱의 ‘진단 정보 복사’에서 확인할 수 있습니다." : diagnostic;
    }

    private void showOrigin(String url) {
        // Never display OAuth query parameters, fragments, account identifiers or tokens.
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                origin.setText("보안 연결 주소를 확인해 주세요");
                return;
            }
            origin.setText("현재 사이트: https://" + uri.getHost()
                    + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort()));
        } catch (Exception exception) {
            origin.setText("사이트 주소 확인 불가");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!finished) {
            // Keep the actual parser/network cause instead of replacing it with a generic close.
            if (terminalFailure == null) {
                UplusScreenParser.Status status = lastObservation != null
                        && (lastObservation.status == UplusScreenParser.Status.LOGIN_REQUIRED
                        || lastObservation.status == UplusScreenParser.Status.AMBIGUOUS)
                        ? lastObservation.status : UplusScreenParser.Status.NO_VALUE;
                String reason = lastObservation == null
                        ? "공식 웹 수량 영역을 확인하기 전에 연결 화면 닫힘"
                        : safeDiagnostic(lastObservation.diagnostic);
                OfficialUsageStore.completeWeb(this, request,
                        UplusScreenParser.Result.status(status, reason
                                + "\n연결 화면 닫힘 · 이전 값과 확인 시각 유지"));
            }
            WidgetUpdater.updateAll(this);
        }
        finished = true;
        destroyWebView();
        super.onDestroy();
    }

    private void destroyWebView() {
        WebView oldView = webView;
        webView = null;
        if (oldView == null) return;
        if (client == null || !client.isRendererGone()) {
            try {
                oldView.stopLoading();
            } catch (RuntimeException ignored) { }
        }
        if (oldView.getParent() instanceof ViewGroup) {
            ((ViewGroup) oldView.getParent()).removeView(oldView);
        }
        try {
            oldView.destroy();
        } catch (RuntimeException ignored) { }
    }

    private void applyInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }
}
