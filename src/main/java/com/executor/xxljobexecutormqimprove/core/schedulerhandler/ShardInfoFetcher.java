package com.executor.xxljobexecutormqimprove.core.schedulerhandler;

import com.executor.xxljobexecutormqimprove.core.context.ShardContext;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

public class ShardInfoFetcher {
    @XxlJob("shardInfoFetcher")
    public void fetchShardInfo() throws Exception {
        int index = XxlJobContext.getXxlJobContext().getShardIndex();
        int total = XxlJobContext.getXxlJobContext().getShardTotal();

        // 更新到全局变量/配置中心/静态类中供时间轮线程使用
        ShardContext.update(index, total);

        XxlJobHelper.log("Shard info updated: index={}, total={}", index, total);
    }

}
