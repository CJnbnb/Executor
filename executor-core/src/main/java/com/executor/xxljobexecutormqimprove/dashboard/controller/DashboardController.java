package com.executor.xxljobexecutormqimprove.dashboard.controller;

import com.executor.xxljobexecutormqimprove.dashboard.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
                                     @RequestParam(required = false) String bizName,
                                     @RequestParam(required = false) String bizGroup,
                                     @RequestParam(required = false) String enable,
                                     @RequestParam(required = false) String process) {
        return dashboardService.tasks(page, size, taskName, bizName, bizGroup, enable, process);
    }

    @PutMapping("/tasks/{id}/toggle")
    public Map<String, Object> toggle(@PathVariable String id) {
        boolean ok = dashboardService.toggleEnable(id);
        return Map.of("success", ok);
    }

    @PutMapping("/tasks/batch/toggle")
    public Map<String, Object> batchToggle(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String enable = (String) body.get("enable");
        boolean ok = dashboardService.batchToggleEnable(ids, enable);
        return Map.of("success", ok);
    }

    @PutMapping("/tasks/{id}/release")
    public Map<String, Object> release(@PathVariable String id) {
        boolean ok = dashboardService.releaseTask(id);
        return Map.of("success", ok);
    }

    @PutMapping("/tasks/batch/release")
    public Map<String, Object> batchRelease(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        boolean ok = dashboardService.batchReleaseTasks(ids);
        return Map.of("success", ok);
    }

    @DeleteMapping("/tasks/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        boolean ok = dashboardService.delete(id);
        return Map.of("success", ok);
    }

    @DeleteMapping("/tasks/batch/delete")
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        boolean ok = dashboardService.batchDelete(ids);
        return Map.of("success", ok);
    }
}
