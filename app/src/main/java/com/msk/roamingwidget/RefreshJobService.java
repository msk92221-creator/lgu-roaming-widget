package com.msk.roamingwidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RefreshJobService extends JobService {
    private static final int PERIODIC_JOB_ID = 27001;
    private static final int IMMEDIATE_JOB_ID = 27002;
    private final Map<Integer, WorkHandle> activeJobs = new ConcurrentHashMap<>();

    static void schedulePeriodic(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(
                PERIODIC_JOB_ID,
                new ComponentName(context, RefreshJobService.class)
        )
                .setPeriodic(TimeUnit.MINUTES.toMillis(30), TimeUnit.MINUTES.toMillis(10))
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    static void scheduleImmediate(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(
                IMMEDIATE_JOB_ID,
                new ComponentName(context, RefreshJobService.class)
        )
                .setOverrideDeadline(0L)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        WorkHandle handle = new WorkHandle();
        WorkHandle previous = activeJobs.put(params.getJobId(), handle);
        if (previous != null) {
            previous.cancel();
        }
        handle.future = AppExecutors.IO.submit(() -> {
            try {
                if (!handle.cancelled.get()) {
                    RoamingTracker.refresh(getApplicationContext());
                }
            } finally {
                activeJobs.remove(params.getJobId(), handle);
                if (!handle.cancelled.get()) {
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        WorkHandle handle = activeJobs.remove(params.getJobId());
        if (handle != null) {
            handle.cancel();
        }
        return true;
    }

    private static final class WorkHandle {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile Future<?> future;

        void cancel() {
            cancelled.set(true);
            Future<?> current = future;
            if (current != null) {
                current.cancel(true);
            }
        }
    }
}
