package com.msk.roamingwidget;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppExecutors {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "roaming-widget-io");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private AppExecutors() {
    }
}
