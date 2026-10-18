package com.msk.roamingwidget;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Process;
import android.os.RemoteException;

final class UsageRepository {
    private UsageRepository() {
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static long queryRoamingMobileBytes(Context context, long startTime, long endTime) {
        NetworkStatsManager manager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("NetworkStatsManager를 사용할 수 없습니다.");
        }

        long roamingBytes = 0L;
        NetworkStats stats = null;
        try {
            stats = manager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    null,
                    startTime,
                    endTime
            );
            if (stats == null) {
                throw new IllegalStateException("Android가 네트워크 통계를 반환하지 않았습니다.");
            }
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            while (stats.hasNextBucket()) {
                if (!stats.getNextBucket(bucket)) {
                    break;
                }
                if (bucket.getRoaming() == NetworkStats.Bucket.ROAMING_YES) {
                    roamingBytes = safeAdd(roamingBytes, bucket.getRxBytes());
                    roamingBytes = safeAdd(roamingBytes, bucket.getTxBytes());
                }
            }
            return roamingBytes;
        } catch (RemoteException exception) {
            throw new IllegalStateException("네트워크 사용량을 읽지 못했습니다.", exception);
        } finally {
            if (stats != null) {
                stats.close();
            }
        }
    }

    private static long safeAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
