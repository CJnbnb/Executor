package com.executor.xxljobexecutormqimprove.util;

import org.quartz.CronExpression;

import java.text.ParseException;
import java.util.Date;

public class CronTimeUtil {

    /**
     * 计算下一个触发时间的时间戳（毫秒）
     * @param cronExpr cron表达式
     * @param fromTime 基准时间（通常为当前时间）
     * @return 下一个触发时间的时间戳（毫秒），如果无下次触发则返回-1
     */
    public static long getNextTriggerTime(String cronExpr, long fromTime) throws ParseException {
        CronExpression cron = new CronExpression(cronExpr);
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