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
import org.json.JSONTokener;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Normal official site browsing; never exports cookies, headers, account details or raw HTML. */
final class OfficialWebPage {
    static final String URL = "https://www.lguplus.com/plan/roaming/my-roaming";
    static final String BALANCE_PATH = "/uhdc/fo/myin/roam/v1/data-rmndlist";

    // These aggregate selectors were verified against the signed-in official web page and
    // its public rendering source. Never substitute the basic-data component for the total.
    static final String READ_SCRIPT = """
            (() => {
              const out = (state, remaining = '', total = '') =>
                  JSON.stringify({state, remaining, total});
              const host = location.hostname.toLowerCase();
              if (location.protocol !== 'https:' ||
                  (location.port !== '' && location.port !== '443')) return out('unsupported');
              if (host === 'account.lguplus.com' || /^\\/login(?:\\/|$)/.test(location.pathname))
                return out('login');
              if (!['www.lguplus.com', 'm.lguplus.com'].includes(host) ||
                  !/^\\/plan\\/roaming\\/my-roaming\\/?$/.test(location.pathname))
                return out('unsupported');
              const cards = document.querySelectorAll('.white-box.data-use-status');
              if (!cards.length) return out('loading');
              if (cards.length !== 1) return out('ambiguous');
              const card = cards[0];
              const titles = card.querySelectorAll('.data-title-area > h6');
              const values = card.querySelectorAll('.data-title-area .data-info .point');
              const graph = card.querySelector('.bar-graph-area');
              const totals = graph ? graph.querySelectorAll('.data-legend .total-data') : [];
              if (titles.length !== 1 || values.length !== 1 || totals.length !== 1)
                return out('loading');
              if (/무제한|일일|하루/.test(titles[0].textContent || ''))
                return out('unsupported');
              const completed = performance.getEntriesByType('resource').some(entry => {
                try {
                  const url = new URL(entry.name);
                  return url.protocol === 'https:' &&
                    (url.hostname === 'lguplus.com' || url.hostname.endsWith('.lguplus.com')) &&
                    url.pathname === '/uhdc/fo/myin/roam/v1/data-rmndlist' &&
                    entry.responseEnd > 0;
                } catch (_) { return false; }
              });
              if (!completed) return out('loading');
              return out('ready', (values[0].textContent || '').trim(),
                  (totals[0].textContent || '').trim());
            })()
            """;

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
        view.evaluateJavascript(READ_SCRIPT, encoded -> {
            try {
                Object decoded = new JSONTokener(encoded).nextValue();
                if (!(decoded instanceof String) || ((String) decoded).length() > 512) {
                    callback.accept(unavailable("공식 웹 응답 형식을 확인하지 못함"));
                    return;
                }
                JSONObject value = new JSONObject((String) decoded);
                callback.accept(OfficialWebBalanceParser.parse(value.optString("state"),
                        value.optString("remaining"), value.optString("total")));
            } catch (Exception exception) {
                callback.accept(unavailable("공식 웹 수량 영역을 확인하지 못함"));
            }
        });
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
            if (request.isForMainFrame()
                    || BALANCE_PATH.equals(request.getUrl().getPath())) {
                failure.accept(unavailable("공식 웹 네트워크 오류 · 이전 값 유지"));
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                WebResourceResponse response) {
            if (request.isForMainFrame()
                    || BALANCE_PATH.equals(request.getUrl().getPath())) {
                failure.accept(response.getStatusCode() == 401
                        ? UplusScreenParser.Result.status(UplusScreenParser.Status.LOGIN_REQUIRED,
                        "공식 웹 로그인 만료 · 직접 다시 로그인 필요")
                        : unavailable("공식 웹 조회 오류 · 이전 값 유지"));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            rendererGone = true;
            failure.accept(unavailable("공식 웹 렌더러 종료 · 이전 값 유지"));
            return true;
        }
    }
}
