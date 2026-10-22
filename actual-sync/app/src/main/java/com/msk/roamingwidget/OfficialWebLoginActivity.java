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
            webView.loadUrl(OfficialWebPage.URL);
        });
        buttons.addView(roaming, new LinearLayout.LayoutParams(0, -2, 2));
        root.addView(buttons);
        webView = OfficialWebPage.create(this);
        client = new OfficialWebPage.Client(true, this::scheduleRead, this::showFailure,
                this::showOrigin);
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
        if (finished || !resumed || reading || webView == null) {
            return;
        }
        reading = true;
        OfficialWebPage.read(webView, result -> {
            reading = false;
            if (finished || !resumed) {
                return;
            }
            if (result.status == UplusScreenParser.Status.SUCCESS
                    && client.hasFreshBalanceRequest()) {
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
                            + "로그인 뒤 홈으로 가면 위의 ‘로밍 조회로 이동’을 누르세요.");
                } else if (OfficialWebPage.isRoamingUrl(webView.getUrl())) {
                    message.setText("공식 로밍 데이터를 확인 중입니다.\n"
                            + "수치가 보이는데 연결되지 않으면 이전 화면으로 돌아가 진단을 확인하세요.");
                }
            }
            scheduleRead();
        });
    }

    private void showFailure(UplusScreenParser.Result result) {
        if (!finished) {
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
            OfficialUsageStore.completeWeb(this, request, OfficialWebPage.unavailable(
                    "공식 웹 연결을 완료하지 않고 닫음 · 이전 값 유지"));
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
