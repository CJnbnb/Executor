package com.executor.xxljobexecutormqimprove.core.context;

public class ShardContext {
    private static volatile int shardIndex = 0;
    private static volatile int shardTotal = 1;

    public static void update(int index, int total) {
        shardIndex = index;
        shardTotal = total;
    }

    public static boolean isMine(long taskId) {
        return (taskId % shardTotal) == shardIndex;
    }

    public static int getShardIndex() {
        return shardIndex;
    }

    public static int getShardTotal() {
        return shardTotal;
    }
}

