package com.executor.xxljobexecutormqimprove.controller;

import com.executor.xxljobexecutormqimprove.model.MonitorTaskVO;
import com.executor.xxljobexecutormqimprove.service.MonitorTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class DashboardController {
    @Autowired
    private MonitorTaskService monitorTaskService;

    // 总览页：只展示统计卡片
    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        int total = monitorTaskService.countAll();
        int running = monitorTaskService.countByProcess("processing");
        int done = monitorTaskService.countByProcess("done");
        int waiting = monitorTaskService.countByProcess("waiting");
        int exception = monitorTaskService.countByProcess("exception");
        model.addAttribute("total", total);
        model.addAttribute("running", running);
        model.addAttribute("done", done);
        model.addAttribute("waiting", waiting);
        model.addAttribute("exception", exception);
        return "dashboard";
    }

    // 任务列表页
    @GetMapping("/task/list")
    public String taskList(Model model) {
        List<MonitorTaskVO> allTasks = monitorTaskService.selectAllTasks();
        model.addAttribute("tasks", allTasks);
        return "task_list";
    }

    // 异常任务页
    @GetMapping("/task/exception")
    public String exceptionList(Model model) {
        List<MonitorTaskVO> exceptionTasks = monitorTaskService.selectExceptionTasks();
        model.addAttribute("tasks", exceptionTasks);
        return "task_exception";
    }

    // 最近任务页（可选）
    @GetMapping("/task/recent")
    public String recentTasks(Model model) {
        List<MonitorTaskVO> recentTasks = monitorTaskService.selectRecentTasks(20);
        model.addAttribute("tasks", recentTasks);
        return "recent_tasks";
    }

    // 任务详情页
    @GetMapping("/task/detail/{id}")
    public String taskDetail(@PathVariable("id") String id, Model model) {
        MonitorTaskVO task = monitorTaskService.selectTaskById(id);
        model.addAttribute("task", task);
        return "task_detail";
    }
} 