package com.executor.xxljobexecutormqimprove.dashboard.controller;

import com.executor.xxljobexecutormqimprove.dashboard.service.DashboardService;
import com.executor.xxljobexecutormqimprove.metrics.MetricsCollector;
import com.executor.xxljobexecutormqimprove.model.MonitorTaskVO;
import com.executor.xxljobexecutormqimprove.model.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.service.MonitorTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private MonitorTaskService monitorTaskService;

    @Autowired
    private MetricsCollector metricsCollector;

    // ── HTML monitoring pages ──────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("total", monitorTaskService.countAll());
        model.addAttribute("running", monitorTaskService.countByProcess("processing"));
        model.addAttribute("done", monitorTaskService.countByProcess("done"));
        model.addAttribute("waiting", monitorTaskService.countByProcess("waiting"));
        model.addAttribute("exception", monitorTaskService.countByProcess("exception"));
        model.addAttribute("recentTasks", monitorTaskService.selectRecentTasks(20));
        model.addAttribute("exceptionTasks", monitorTaskService.selectExceptionTasks());
        return "dashboard";
    }

    @GetMapping("/tasks")
    public String taskList(Model model) {
        model.addAttribute("tasks", monitorTaskService.selectAllTasks());
        return "task_list";
    }

    @GetMapping("/tasks/exception")
    public String exceptionList(Model model) {
        model.addAttribute("tasks", monitorTaskService.selectExceptionTasks());
        return "task_exception";
    }

    @GetMapping("/tasks/recent")
    public String recentTasks(Model model) {
        model.addAttribute("tasks", monitorTaskService.selectRecentTasks(20));
        return "recent_tasks";
    }

    @GetMapping("/tasks/{id}")
    public String taskDetail(@PathVariable String id, Model model) {
        MonitorTaskVO task = monitorTaskService.selectTaskById(id);
        model.addAttribute("task", task);
        return "task_detail";
    }

    // ── REST API ───────────────────────────────────────────────────────────

    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> stats() {
        return dashboardService.stats();
    }

    @GetMapping("/api/tasks")
    @ResponseBody
    public Map<String, Object> tasks(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String taskName,
                                     @RequestParam(required = false) String bizName,
                                     @RequestParam(required = false) String bizGroup,
                                     @RequestParam(required = false) String enable,
                                     @RequestParam(required = false) String process,
                                     @RequestParam(required = false) String sortBy,
                                     @RequestParam(required = false) String sortDir) {
        return dashboardService.tasks(page, size, taskName, bizName, bizGroup, enable, process, sortBy, sortDir);
    }

    @GetMapping("/api/tasks/{id}")
    @ResponseBody
    public Map<String, Object> getTaskDetail(@PathVariable String id) {
        CommonTaskEntity task = dashboardService.getTaskById(id);
        if (task == null) {
            return Map.of("success", false, "error", "Task not found");
        }
        return Map.of("success", true, "data", task);
    }

    @PutMapping("/api/tasks/{id}/toggle")
    @ResponseBody
    public Map<String, Object> toggle(@PathVariable String id) {
        return Map.of("success", dashboardService.toggleEnable(id));
    }

    @PutMapping("/api/tasks/batch/toggle")
    @ResponseBody
    public Map<String, Object> batchToggle(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        String enable = (String) body.get("enable");
        return Map.of("success", dashboardService.batchToggleEnable(ids, enable));
    }

    @PutMapping("/api/tasks/{id}/release")
    @ResponseBody
    public Map<String, Object> release(@PathVariable String id) {
        return Map.of("success", dashboardService.releaseTask(id));
    }

    @PutMapping("/api/tasks/batch/release")
    @ResponseBody
    public Map<String, Object> batchRelease(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        return Map.of("success", dashboardService.batchReleaseTasks(ids));
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable String id) {
        return Map.of("success", dashboardService.delete(id));
    }

    @DeleteMapping("/api/tasks/batch")
    @ResponseBody
    public Map<String, Object> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        return Map.of("success", dashboardService.batchDelete(ids));
    }

    @GetMapping("/api/metrics")
    @ResponseBody
    public Map<String, Long> metrics() {
        return metricsCollector.snapshot();
    }
}
