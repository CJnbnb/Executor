package com.executor.xxljobexecutormqimprove.dashboard.controller;

import com.executor.xxljobexecutormqimprove.dashboard.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return dashboardService.stats();
    }

    @GetMapping("/tasks")
    public Map<String, Object> tasks(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String taskName,
                                     @RequestParam(required = false) String bizName) {
        return dashboardService.tasks(page, size, taskName, bizName);
    }

    @PutMapping("/tasks/{id}/toggle")
    public Map<String, Object> toggle(@PathVariable String id) {
        boolean ok = dashboardService.toggleEnable(id);
        return Map.of("success", ok);
    }

    @DeleteMapping("/tasks/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        boolean ok = dashboardService.delete(id);
        return Map.of("success", ok);
    }
}
