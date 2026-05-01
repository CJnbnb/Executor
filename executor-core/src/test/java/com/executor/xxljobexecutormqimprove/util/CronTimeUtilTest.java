package com.executor.xxljobexecutormqimprove.util;

import org.junit.jupiter.api.Test;
import java.text.ParseException;
import static org.junit.jupiter.api.Assertions.*;

class CronTimeUtilTest {

    @Test
    void testValidCronExpression() throws Exception {
        String cron = "0 0 13 * * ?";
        long now = System.currentTimeMillis();
        long next = CronTimeUtil.getNextTriggerTime(cron, now);
        assertTrue(next > now, "Next trigger should be in the future");
    }

    @Test
    void testInvalidCronExpression() {
        assertThrows(ParseException.class, () -> {
            CronTimeUtil.getNextTriggerTime("invalid", System.currentTimeMillis());
        });
    }

    @Test
    void testEveryMinuteCron() throws Exception {
        String cron = "0 * * * * ?";
        long now = System.currentTimeMillis();
        long next = CronTimeUtil.getNextTriggerTime(cron, now);
        assertTrue(next > now);
        long diff = next - now;
        assertTrue(diff <= 60_000, "Should trigger within 1 minute, got " + diff + "ms");
    }

    @Test
    void testEmptyCronExpression() {
        assertThrows(ParseException.class, () -> {
            CronTimeUtil.getNextTriggerTime("", System.currentTimeMillis());
        });
    }

    @Test
    void testNullCronExpression() {
        assertThrows(RuntimeException.class, () -> {
            CronTimeUtil.getNextTriggerTime(null, System.currentTimeMillis());
        });
    }
}
