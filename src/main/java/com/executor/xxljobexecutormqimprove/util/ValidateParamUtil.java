package com.executor.xxljobexecutormqimprove.util;

public class ValidateParamUtil {

    /**
     * 校验调度参数格式是否正确，并返回拆分后的参数
     * 格式：bizName,bizGroup
     * @param param 输入参数
     * @return String数组：[0] = bizName, [1] = bizGroup
     * @throws IllegalArgumentException 参数为空或格式错误
     */
    public static String[] validateAndParseJobParam(String param) {
        if (param == null || param.trim().isEmpty()) {
            throw new IllegalArgumentException("参数不能为空");
        }

        String[] parts = param.trim().split(",");
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            throw new IllegalArgumentException("参数格式错误，正确格式为：bizName,bizGroup");
        }

        return new String[] { parts[0].trim(), parts[1].trim() };
    }
}
