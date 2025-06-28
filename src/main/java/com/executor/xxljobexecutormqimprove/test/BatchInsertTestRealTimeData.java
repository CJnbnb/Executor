package com.executor.xxljobexecutormqimprove.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class BatchInsertTestRealTimeData {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/xxl_job_executor_mq?useSSL=false&serverTimezone=UTC",
                "root", "Q23897876p"
        );
        String sql = "INSERT INTO user_scheduled_realtime_task " +
                "(id, task_name, biz_name, biz_group, next_trigger_time, process, enable, payload,scheduled_type,create_at,update_at,topic,task_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 1; i <= 50000; i++) {
            ps.setString(1, "test-xxx" + i);
            ps.setString(2, "压测任务-" + i); // task_name，保证唯一
            ps.setString(3, "testBiz");
            ps.setString(4, "testGroup");
            ps.setLong(5, System.currentTimeMillis()-3600); // 未来1小时
            ps.setString(6, "pending");
            ps.setString(7, "1");
            ps.setString(8, "{}");
            ps.setString(9,"2");
            ps.setTimestamp(10, java.sql.Timestamp.valueOf("2025-06-22 16:45:00"));
            ps.setTimestamp(11, java.sql.Timestamp.valueOf("2025-06-22 16:45:00"));
            ps.setString(12,"order-process");
            ps.setString(13,"testid-"+ i);
            ps.addBatch();
        }
        ps.executeBatch();
        ps.close();
        conn.close();
        System.out.println("插入完成");
    }
}