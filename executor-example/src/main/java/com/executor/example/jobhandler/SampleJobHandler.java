package com.executor.example.jobhandler;

import com.executor.sdk.ExecutorSdkClient;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * XXL-Job 示例 JobHandler（Bean 模式）。
 * <p>
 * 开发步骤：
 * <ol>
 *   <li>在 Spring Bean 中开发 Job 方法</li>
 *   <li>添加 @XxlJob 注解，value 对应调度中心新建任务的 JobHandler</li>
 *   <li>通过 XxlJobHelper.log 打印执行日志</li>
 *   <li>通过 XxlJobHelper.handleFail/handleSuccess 设置任务结果</li>
 * </ol>
 * <p>
 * 同时演示如何使用 ExecutorSdkClient 向 Executor 动态注册新任务。
 * </p>
 */
@Component
public class SampleJobHandler {

    private static final Logger log = LoggerFactory.getLogger(SampleJobHandler.class);

    @Autowired(required = false)
    private ExecutorSdkClient sdkClient;

    /**
     * 1、简单任务示例
     * <p>参数：无</p>
     */
    @XxlJob("demoJobHandler")
    public void demoJobHandler() throws Exception {
        XxlJobHelper.log("XXL-JOB Executor Example, Hello World.");

        for (int i = 0; i < 5; i++) {
            XxlJobHelper.log("beat at: {}", i);
            TimeUnit.SECONDS.sleep(1);
        }

        XxlJobHelper.handleSuccess("demo 任务执行成功");
    }

    /**
     * 2、分片广播任务
     * <p>模拟分布式分片处理场景</p>
     */
    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() throws Exception {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 模拟业务逻辑：每个分片处理属于自己的一部分数据
        for (int i = 0; i < shardTotal; i++) {
            if (i == shardIndex) {
                XxlJobHelper.log("第 {} 片, 命中分片开始处理", i);
                // 实际业务逻辑在此...
            } else {
                XxlJobHelper.log("第 {} 片, 忽略", i);
            }
        }

        XxlJobHelper.handleSuccess("分片任务执行成功");
    }

    /**
     * 3、带参数的任务
     * <p>参数格式：bizName,bizGroup</p>
     * <p>模拟 Executor 中 ProducerHandler 的调度方式</p>
     */
    @XxlJob("paramJobHandler")
    public void paramJobHandler() throws Exception {
        String param = XxlJobHelper.getJobParam();
        XxlJobHelper.log("接收到参数: {}", param);

        if (param == null || param.trim().isEmpty()) {
            XxlJobHelper.handleFail("参数为空，请传入 bizName,bizGroup");
            return;
        }

        String[] parts = param.split(",");
        if (parts.length < 2) {
            XxlJobHelper.handleFail("参数格式错误，期望: bizName,bizGroup");
            return;
        }

        String bizName = parts[0].trim();
        String bizGroup = parts[1].trim();

        XxlJobHelper.log("执行业务调度: bizName={}, bizGroup={}", bizName, bizGroup);

        // 模拟：通过 ExecutorSdkClient 动态注册一个一次性子任务
        sdkClient.newTask("auto-registered-task")
                .biz(bizName, bizGroup)
                .once(System.currentTimeMillis() + 60_000)
                .payload("{\"action\":\"auto-demo\",\"source\":\"paramJobHandler\"}")
                .schedule();

        XxlJobHelper.log("已通过 SDK 注册一次性子任务");
        XxlJobHelper.handleSuccess("参数任务执行成功");
    }

    /**
     * 4、使用 ExecutorSdkClient 注册 Cron 定时任务
     * <p>演示业务方如何动态注册定时任务到 Executor 调度引擎</p>
     */
    @XxlJob("registerCronTaskHandler")
    public void registerCronTaskHandler() throws Exception {
        String param = XxlJobHelper.getJobParam();
        // 参数格式: taskName,bizName,bizGroup,cronExpression
        // 示例: myTask,order,bizA,0/30 * * * * ?

        if (param == null || param.trim().isEmpty()) {
            XxlJobHelper.handleFail("参数为空，期望: taskName,bizName,bizGroup,cronExpression");
            return;
        }

        String[] parts = param.split(",");
        if (parts.length < 4) {
            XxlJobHelper.handleFail("参数不足，期望: taskName,bizName,bizGroup,cronExpression");
            return;
        }

        String taskName = parts[0].trim();
        String bizName = parts[1].trim();
        String bizGroup = parts[2].trim();
        String cronExpression = parts[3].trim();

        // 通过 Builder 模式构建并注册 Cron 定时任务
        boolean ok = sdkClient.newTask(taskName)
                .biz(bizName, bizGroup)
                .cron(cronExpression)
                .payload("{\"registeredBy\":\"registerCronTaskHandler\"}")
                .topic("executorPool")
                .schedule();

        XxlJobHelper.log("Cron 任务注册结果: {}, taskName={}, cron={}", ok, taskName, cronExpression);
        if (ok) {
            XxlJobHelper.handleSuccess("任务注册成功: " + taskName);
        } else {
            XxlJobHelper.handleFail("任务注册失败: " + taskName);
        }
    }

    /**
     * 5、生命周期任务示例：演示 init/destroy 回调
     */
    @XxlJob(value = "lifecycleJobHandler", init = "initMethod", destroy = "destroyMethod")
    public void lifecycleJobHandler() throws Exception {
        XxlJobHelper.log("XXL-JOB, lifecycle demo.");
        XxlJobHelper.handleSuccess("生命周期任务执行成功");
    }

    public void initMethod() {
        log.info(">>> lifecycleJobHandler init 回调触发");
    }

    public void destroyMethod() {
        log.info(">>> lifecycleJobHandler destroy 回调触发");
    }
}
