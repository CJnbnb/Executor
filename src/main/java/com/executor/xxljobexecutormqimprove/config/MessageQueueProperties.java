package com.executor.xxljobexecutormqimprove.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xxl.job.process")
public class MessageQueueProperties {

    /** NameServer address, required for RocketMQ */
    private String nameserver;

    /** Topic for task messages */
    private String topic = "executorConsumeTask";

    /** Consumer group name */
    private String group = "executorConsumeMessageGroup";

    /** Producer group name (from xxl.job.producer) */
    private String producerGroup = "executorProduceGroup";

    /** MQ implementation type: rocketmq, kafka, etc. */
    private String type = "rocketmq";

    public String getNameserver() {
        return nameserver;
    }

    public void setNameserver(String nameserver) {
        this.nameserver = nameserver;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
