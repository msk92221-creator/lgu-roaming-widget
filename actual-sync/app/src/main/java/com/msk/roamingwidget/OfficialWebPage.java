package com.msk.roamingwidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Normal official site browsing; never exports cookies, headers, account details or raw HTML. */
final class OfficialWebPage {
    static final String URL = "https://www.lguplus.com/plan/roaming/my-roaming";
    static final String BALANCE_PATH = "/uhdc/fo/myin/roam/v1/data-rmndlist";

    private static String cachedReaderScript;

    private OfficialWebPage() { }

    @SuppressLint("SetJavaScriptEnabled")
    static WebView create(Context context) {
        WebView view = new WebView(context);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        // Keep the genuine WebView user-agent: do not bypass an identity provider's policy.
        return view;
    }

    static boolean isRoamingUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ("www.lguplus.com".equalsIgnoreCase(host)
                    || "m.lguplus.com".equalsIgnoreCase(host))
                    && ("/plan/roaming/my-roaming".equals(uri.getPath())
                    || "/plan/roaming/my-roaming/".equals(uri.getPath()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static void read(WebView view, Consumer<UplusScreenParser.Result> callback) {
        final String script;
        try {
            script = readerScript(view.getContext());
        } catch (IOException exception) {
            callback.accept(unavailable("로밍 읽기 모듈을 불러오지 못함"));
            return;
        }
        view.evaluateJavascript(script, encoded -> {
            try {
                Object decoded = new JSONTokener(encoded).nextValue();
                if (!(decoded instanceof String) || ((String) decoded).length() > 8192) {
                    callback.accept(unavailable("공식 웹 응답 형식을 확인하지 못함"));
                    return;
                }
                JSONObject value = new JSONObject((String) decoded);
                UplusScreenParser.Result result = OfficialWebBalanceParser.parse(value.optString("state"),
                        value.optString("remaining"), value.optString("total"));
                callback.accept(result.withDiagnostic(result.diagnostic + "\n" + safeReadDetails(value)));
            } catch (Exception exception) {
                callback.accept(unavailable("공식 웹 수량 영역을 확인하지 못함"));
            }
        });
    }

    private static synchronized String readerScript(Context context) throws IOException {
        if (cachedReaderScript == null) {
            try (InputStream input = context.getAssets().open("official_roaming_reader.js");
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                cachedReaderScript = output.toString("UTF-8");
            }
        }
        return cachedReaderScript;
    }

    private static String safeReadDetails(JSONObject value) {
        String layout = "mobile".equals(value.optString("layout")) ? "모바일" : "PC";
        String stage;
        switch (value.optString("stage")) {
            case "ready": stage = "총 데이터 인식·조회 응답 완료"; break;
            case "mobile_total_missing": stage = "모바일 총 데이터 행 확인 대기"; break;
            case "desktop_total_missing": stage = "PC 총 데이터 영역 확인 대기"; break;
            case "waiting_balance_response": stage = "총 데이터 행 인식됨 · 조회 응답 완료 확인 대기"; break;
            case "multiple_total_rows": stage = "서로 다른 총 데이터 행 여러 개 감지"; break;
            case "unsupported_plan": stage = "지원하지 않는 요금제 표시"; break;
            case "node_limit": stage = "화면 노드 검사 한도 초과"; break;
            case "login": stage = "공식 웹 로그인 화면"; break;
            default: stage = "공식 로밍 화면 경로 확인 필요"; break;
        }
        StringBuilder result = new StringBuilder(layout + " · " + stage);
        JSONArray assets = value.optJSONArray("assets");
        boolean addedAsset = false;
        if (assets != null) {
            for (int index = 0; index < Math.min(12, assets.length()); index++) {
                String asset = assets.optString(index);
                if (!isPublicScriptReference(asset)) continue;
                if (!addedAsset) result.append("\n공개 스크립트 참조(인증정보 없음):");
                addedAsset = true;
                result.append('\n').append(asset);
            }
        }
        return result.toString();
    }

    static boolean isPublicScriptReference(String value) {
        if (value == null || value.length() > 300) return false;
        try {
            URI uri = URI.create(value);
            String path = uri.getRawPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && ("m.lguplus.com".equalsIgnoreCase(uri.getHost())
                    || "www.lguplus.com".equalsIgnoreCase(uri.getHost()))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && path != null && path.length() <= 220
                    && !path.contains("..")
                    && path.matches("/(?:static|_nuxt|_next|assets)/[A-Za-z0-9_./-]+\\.js");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isCarrierHttps(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (host.equalsIgnoreCase("lguplus.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".lguplus.com"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static UplusScreenParser.Result unavailable(String safeReason) {
        return UplusScreenParser.Result.status(UplusScreenParser.Status.NO_VALUE, safeReason);
    }

    static final class Client extends WebViewClient {
        private final boolean interactive;
        private final Runnable pageFinished;
        private final Consumer<UplusScreenParser.Result> failure;
        private final Consumer<String> navigation;
        private final AtomicBoolean freshBalanceRequest = new AtomicBoolean();
        private boolean rendererGone;
        private String activeMainUrl;

        Client(boolean interactive, Runnable pageFinished,
                Consumer<UplusScreenParser.Result> failure) {
            this(interactive, pageFinished, failure, url -> { });
        }

        Client(boolean interactive, Runnable pageFinished,
                Consumer<UplusScreenParser.Result> failure, Consumer<String> navigation) {
            this.interactive = interactive;
            this.pageFinished = pageFinished;
            this.failure = failure;
            this.navigation = navigation;
        }

        boolean hasFreshBalanceRequest() {
            return freshBalanceRequest.get();
        }

        boolean isRendererGone() {
            return rendererGone;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap icon) {
            freshBalanceRequest.set(false);
            activeMainUrl = url;
            navigation.accept(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            // Observe only. The official page performs its own session-authenticated request;
            // no cookie extraction, HTTP replay, response replacement or header modification.
            if ("GET".equals(request.getMethod()) && isCarrierHttps(request.getUrl().toString())
                    && BALANCE_PATH.equals(request.getUrl().getPath())) {
                freshBalanceRequest.set(true);
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            boolean https = "https".equalsIgnoreCase(request.getUrl().getScheme());
            if (!request.isForMainFrame()) return !https;
            if (!https || (!interactive && !isCarrierHttps(url))) {
                failure.accept(UplusScreenParser.Result.status(
                        UplusScreenParser.Status.LOGIN_REQUIRED,
                        "공식 웹에서 직접 로그인 필요 · 외부 앱 자동 실행 안 함"));
                return true;
            }
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageFinished.run();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            if (isCarrierHttps(error.getUrl())) {
                failure.accept(unavailable("공식 웹 보안 연결 실패 · 인증서 우회 안 함"));
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                WebResourceError error) {
            if (request.isForMainFrame() && !isCurrentMainFrame(view, request)) return;
            if (request.isForMainFrame()
                    || BALANCE_PATH.equals(request.getUrl().getPath())) {
                failure.accept(unavailable("공식 웹 네트워크 오류 · 이전 값 유지"));
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                WebResourceResponse response) {
            if (request.isForMainFrame() && !isCurrentMainFrame(view, request)) return;
            if (request.isForMainFrame()
                    || BALANCE_PATH.equals(request.getUrl().getPath())) {
                failure.accept(response.getStatusCode() == 401
                        ? UplusScreenParser.Result.status(UplusScreenParser.Status.LOGIN_REQUIRED,
                        "공식 웹 로그인 만료 · 직접 다시 로그인 필요")
                        : unavailable("공식 웹 조회 오류 · 이전 값 유지"));
            }
        }

        private boolean isCurrentMainFrame(WebView view, WebResourceRequest request) {
            String current = activeMainUrl != null ? activeMainUrl : view.getUrl();
            return current == null || sameNavigation(current, request.getUrl().toString());
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            rendererGone = true;
            failure.accept(unavailable("공식 웹 렌더러 종료 · 이전 값 유지"));
            return true;
        }
    }

    static boolean sameNavigation(String first, String second) {
        if (first == null || second == null) return false;
        // Comparison stays in memory. Never include login URLs/queries in diagnostics.
        int firstHash = first.indexOf('#');
        int secondHash = second.indexOf('#');
        return (firstHash < 0 ? first : first.substring(0, firstHash)).equals(
                secondHash < 0 ? second : second.substring(0, secondHash));
    }
}
