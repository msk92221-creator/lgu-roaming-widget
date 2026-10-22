package com.msk.roamingwidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

/** Schedules a bounded, silent web refresh. It never launches an Activity. */
final class BackgroundRefresh {
    static final int JOB_ID = 73001;
    static final String EXTRA_REQUEST_TOKEN = "official_web_request_token";

    private BackgroundRefresh() {
    }

    static synchronized boolean request(Context context) {
        Context app = context.getApplicationContext();
        JobScheduler scheduler = scheduler(app);
        if (!WebSessionState.isConfigured(app)) {
            cancelScheduled(scheduler);
            OfficialUsageStore.beginSync(app);
            long token = OfficialUsageStore.syncRequestTimestamp(app);
            complete(app, token, UplusScreenParser.Status.LOGIN_REQUIRED,
                    "공식 웹에 한 번 로그인해 주세요 · 앱 자동 실행 안 함");
            return false;
        }

        long currentToken = OfficialUsageStore.syncRequestTimestamp(app);
        if (scheduler != null) {
            try {
                JobInfo pending = scheduler.getPendingJob(JOB_ID);
                if (pending != null
                        && pending.getExtras().getLong(EXTRA_REQUEST_TOKEN, 0L) == currentToken
                        && OfficialUsageStore.isCurrentRequest(app, currentToken)) {
                    WidgetUpdater.updateAll(app);
                    return true;
                }
            } catch (RuntimeException ignored) {
                // A failed inspection must not leave a permanently waiting widget.
            }
        }

        OfficialUsageStore.beginSync(app);
        long token = OfficialUsageStore.syncRequestTimestamp(app);
        WidgetUpdater.updateAll(app);
        if (scheduler == null) {
            complete(app, token, UplusScreenParser.Status.NO_VALUE,
                    "백그라운드 조회를 예약하지 못함 · 이전 값 유지");
            return false;
        }

        PersistableBundle extras = new PersistableBundle();
        extras.putLong(EXTRA_REQUEST_TOKEN, token);
        ComponentName service = new ComponentName(app, WebRefreshJobService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                JobInfo expedited = new JobInfo.Builder(JOB_ID, service)
                        .setExtras(extras)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setExpedited(true)
                        .build();
                if (scheduler.schedule(expedited) == JobScheduler.RESULT_SUCCESS) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Expedited quota or platform restrictions can require a regular job.
            }
        }

        try {
            JobInfo regular = new JobInfo.Builder(JOB_ID, service)
                    .setExtras(extras)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(10_000L)
                    .build();
            if (scheduler.schedule(regular) == JobScheduler.RESULT_SUCCESS) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // A scheduling failure does not invalidate the last successful value.
        }
        complete(app, token, UplusScreenParser.Status.NO_VALUE,
                "백그라운드 조회를 예약하지 못함 · 이전 값 유지");
        return false;
    }

    static synchronized void cancel(Context context) {
        Context app = context.getApplicationContext();
        cancelScheduled(scheduler(app));
        long token = OfficialUsageStore.syncRequestTimestamp(app);
        if (OfficialUsageStore.isCurrentRequest(app, token)) {
            complete(app, token, UplusScreenParser.Status.NO_VALUE,
                    "백그라운드 조회 취소 · 이전 값 유지");
        }
    }

    private static JobScheduler scheduler(Context context) {
        try {
            return context.getSystemService(JobScheduler.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void cancelScheduled(JobScheduler scheduler) {
        if (scheduler != null) {
            try {
                scheduler.cancel(JOB_ID);
            } catch (RuntimeException ignored) {
                // Invalidating the request token below also prevents stale saves.
            }
        }
    }

    private static void complete(
            Context context, long token, UplusScreenParser.Status status, String diagnostic
    ) {
        OfficialUsageStore.completeWeb(context, token,
                UplusScreenParser.Result.status(status, diagnostic));
        WidgetUpdater.updateAll(context);
    }
}
