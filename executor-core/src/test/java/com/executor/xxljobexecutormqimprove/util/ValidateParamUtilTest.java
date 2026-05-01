package com.executor.xxljobexecutormqimprove.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidateParamUtilTest {

    @Test
    void testValidParam() {
        String[] result = ValidateParamUtil.validateAndParseJobParam("bizName,bizGroup");
        assertEquals(2, result.length);
        assertEquals("bizName", result[0]);
        assertEquals("bizGroup", result[1]);
    }

    @Test
    void testNullParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam(null);
        });
    }

    @Test
    void testEmptyParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam("");
        });
    }

    @Test
    void testWhitespaceOnlyParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam("   ");
        });
    }

    @Test
    void testMissingSecondPart() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam("bizName,");
        });
    }

    @Test
    void testMissingFirstPart() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam(",bizGroup");
        });
    }

    @Test
    void testExtraCommas() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam("a,b,c");
        });
    }

    @Test
    void testWhitespaceTrimming() {
        String[] result = ValidateParamUtil.validateAndParseJobParam(" bizName , bizGroup ");
        assertEquals("bizName", result[0]);
        assertEquals("bizGroup", result[1]);
    }

    @Test
    void testSingleToken() {
        assertThrows(IllegalArgumentException.class, () -> {
            ValidateParamUtil.validateAndParseJobParam("onlyOne");
        });
    }
}
