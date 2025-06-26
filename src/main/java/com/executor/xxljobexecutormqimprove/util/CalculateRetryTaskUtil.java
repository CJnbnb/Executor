package com.executor.xxljobexecutormqimprove.util;

import java.util.concurrent.TimeUnit;

public class CalculateRetryTaskUtil {

    /**
     * 根据重试次数和上次触发时间戳，计算下次重试的时间戳（毫秒）
     * @param retryCount 当前是第几次重试（从0开始）
     * @param lastTriggerTimestamp 上一次触发的时间戳（毫秒）
     * @return 下次重试的时间戳（毫秒）
     */
    public static long calculateNextTriggerTimestamp(int retryCount, long lastTriggerTimestamp) {
        long delayMillis;
        if (retryCount >= 0 && retryCount <= 3) {
            // 第0-3次，每次延迟10秒
            delayMillis = TimeUnit.SECONDS.toMillis(10);
        } else if (retryCount >= 4 && retryCount <= 5) {
            // 第4-5次，每次延迟10分钟
            delayMillis = TimeUnit.MINUTES.toMillis(10);
        } else if (retryCount >= 6 && retryCount <= 10) {
            // 第6-10次，每次延迟1小时
            delayMillis = TimeUnit.HOURS.toMillis(1);
        } else {
            // 超过10次，默认延迟1小时
            delayMillis = TimeUnit.HOURS.toMillis(1);
        }
        return lastTriggerTimestamp + delayMillis;
    }
}