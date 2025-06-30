package com.executor.xxljobexecutormqimprove.service;

import com.executor.xxljobexecutormqimprove.mapper.MonitorTaskMapper;
import com.executor.xxljobexecutormqimprove.model.MonitorTaskVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonitorTaskService {
    @Resource
    private MonitorTaskMapper monitorTaskMapper;

    public int countAll() {
        return monitorTaskMapper.countAll();
    }
    public int countByProcess(String process) {
        return monitorTaskMapper.countByProcess(process);
    }
    public List<MonitorTaskVO> selectAllTasks() {
        return monitorTaskMapper.selectAllTasks();
    }
    public List<MonitorTaskVO> selectExceptionTasks() {
        return monitorTaskMapper.selectExceptionTasks();
    }
    public List<MonitorTaskVO> selectRecentTasks(int limit) {
        return monitorTaskMapper.selectRecentTasks(limit);
    }
    public MonitorTaskVO selectTaskById(String id) {
        return monitorTaskMapper.selectTaskById(id);
    }
    public int countCommonByProcess(String process) {
        return monitorTaskMapper.countCommonByProcess(process);
    }
    public int countRealByProcess(String process) {
        return monitorTaskMapper.countRealByProcess(process);
    }
    public List<MonitorTaskVO> selectAllCommonTasks() {
        return monitorTaskMapper.selectAllCommonTasks();
    }
    public List<MonitorTaskVO> selectAllRealTasks() {
        return monitorTaskMapper.selectAllRealTasks();
    }
} 