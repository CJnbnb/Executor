package com.executor.xxljobexecutormqimprove.controller;

import com.executor.xxljobexecutormqimprove.entity.CommonTaskEntity;
import com.executor.xxljobexecutormqimprove.service.TaskManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/manage/task")
public class TaskManageController {
    @Autowired
    private TaskManageService taskManageService;

    // 任务列表页面（分页）
    @GetMapping("/list")
    public String listPage(@RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          Model model) {
        List<CommonTaskEntity> taskList = taskManageService.getTaskList(page, size);
        int total = taskManageService.countTask();
        model.addAttribute("taskList", taskList);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("total", total);
        return "task_list";
    }

    // 单任务修改页面
    @GetMapping("/edit/{id}")
    public String editPage(@PathVariable("id") String id, Model model) {
        CommonTaskEntity task = taskManageService.getTaskById(id);
        model.addAttribute("task", task);
        return "task_edit";
    }

    // 单任务修改提交（此处如需支持编辑，需补充service实现）
    @PostMapping("/edit")
    public String editSubmit(@ModelAttribute CommonTaskEntity task) {
        // TODO: 这里可实现update逻辑
        return "redirect:/manage/task/list";
    }

    // 批量修改页面
    @GetMapping("/batchEdit")
    public String batchEditPage(Model model) {
        List<CommonTaskEntity> taskList = taskManageService.getTaskList(1, 100); // 默认查前100条
        model.addAttribute("taskList", taskList);
        return "task_batch_edit";
    }

    // 批量修改提交（如需支持批量编辑，需补充service实现）
    @PostMapping("/batchEdit")
    public String batchEditSubmit(@ModelAttribute("taskList") List<CommonTaskEntity> taskList) {
        // TODO: 这里可实现批量update逻辑
        return "redirect:/manage/task/list";
    }
}
