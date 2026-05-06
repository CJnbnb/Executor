package com.executor.xxljobexecutormqimprove.util;

import org.quartz.CronExpression;

import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class CronTimeUtil {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final ConcurrentHashMap<String, CronExpression> CRON_CACHE = new ConcurrentHashMap<>();

    /**
     * 计算下一个触发时间的时间戳（毫秒），CronExpression 解析结果会被缓存。
     * @param cronExpr cron表达式
     * @param fromTime 基准时间（通常为当前时间）
     * @return 下一个触发时间的时间戳（毫秒），如果无下次触发则返回-1
     */
    public static long getNextTriggerTime(String cronExpr, long fromTime) throws ParseException {
        CronExpression cron = CRON_CACHE.get(cronExpr);
        if (cron == null) {
            cron = new CronExpression(cronExpr);
            if (CRON_CACHE.size() < MAX_CACHE_SIZE) {
                CronExpression existing = CRON_CACHE.putIfAbsent(cronExpr, cron);
                if (existing != null) {
                    cron = existing;
                }
            }
        }
        Date next = cron.getNextValidTimeAfter(new Date(fromTime));
        return next != null ? next.getTime() : -1;
    }

    public static void main(String[] args) throws Exception {
        String cron = "0 0 13 * * ?"; // 每天13:00
        long now = System.currentTimeMillis();
        long nextTrigger = getNextTriggerTime(cron, now);
        System.out.println("下一个触发时间戳: " + nextTrigger);
        System.out.println("下一个触发时间: " + new Date(nextTrigger));
    }
}