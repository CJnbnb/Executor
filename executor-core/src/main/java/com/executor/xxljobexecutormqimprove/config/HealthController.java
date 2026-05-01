package com.executor.xxljobexecutormqimprove.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("db", checkDb());
        return result;
    }

    private String checkDb() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(3) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }
}
