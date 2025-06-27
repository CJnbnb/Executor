package com.executor.xxljobexecutormqimprove.core.thread;

import com.executor.xxljobexecutormqimprove.Enum.TaskEnableEnum;
import com.executor.xxljobexecutormqimprove.Enum.TriggerEnum;
import com.executor.xxljobexecutormqimprove.entity.RealTimeTaskEntity;
import com.executor.xxljobexecutormqimprove.mapper.RealtimeTaskMapper;
import com.executor.xxljobexecutormqimprove.producer.ProducerMessage;
import jakarta.annotation.Resource;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class SchedulerRealtimeService {
    @Autowired
    private RealtimeTaskMapper realtimeTaskMapper;
    @Resource
    private DataSource dataSource;

    @Autowired
    private JobTriggerPoolHelper jobTriggerPoolHelper;

    private static Logger logger = LoggerFactory.getLogger(SchedulerRealtimeService.class);

    public static final long PRE_READ_MS = 5000;    // pre read

    private Thread scheduleThread;
    private Thread ringThread;
    //保持主存的可见性
    private volatile boolean scheduleThreadToStop = false;
    private volatile boolean ringThreadToStop = false;
    //时间轮
    private volatile static Map<Integer, List<String>> ringData = new ConcurrentHashMap<>();

    public void start(){
        scheduleThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //秒钟对齐
                    TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis()%1000 );
                }catch (Throwable e){
                    if (!scheduleThreadToStop){
                        logger.error(e.getMessage(),e);
                    }
                }
                logger.info("initThread");
                //直接拿xxljob的魔法数字
                int preReadCount = 6000;
                //进行循环线程
                while (!scheduleThreadToStop){
                    //计算时间
                    long start = System.currentTimeMillis();

                    Connection conn = null;
                    Boolean connAutoCommit = null;
                    PreparedStatement preparedStatement = null;
                    boolean preReadSuc = true;

                    try {
                        conn =  dataSource.getConnection();
                        connAutoCommit = conn.getAutoCommit();
                        conn.setAutoCommit(false);

                        preparedStatement = conn.prepareStatement(  "select * from xxl_job_lock where lock_name = 'schedule_lock' for update" );
                        preparedStatement.execute();

                        long nowTime = System.currentTimeMillis();

                        List<RealTimeTaskEntity> scheduleList = realtimeTaskMapper.selectSchedulableTasks(nowTime + PRE_READ_MS, preReadCount);

                        if (scheduleList!=null && scheduleList.size()>0) {
                            // 2、push time-ring
                            for (RealTimeTaskEntity realTimeTask: scheduleList) {

                                // time-ring jump
                                if (nowTime > realTimeTask.getNextTriggerTime() + PRE_READ_MS) {
                                    // 2.1、trigger-expire > 5s：pass && make next-trigger-time
                                    logger.warn(">>>>>>>>>>> xxl-job, schedule misfire, jobId = " + realTimeTask.getId());


                                    // 2、fresh next
                                    refreshNextValidTime(realTimeTask, System.currentTimeMillis());

                                } else if (nowTime > realTimeTask.getNextTriggerTime()) {
                                    // 2.2、trigger-expire < 5s：direct-trigger && make next-trigger-time

                                    // TODO
                                    // 1、trigger
                                    jobTriggerPoolHelper.trigger(realTimeTask.getId());

                                    logger.debug(">>>>>>>>>>> xxl-job, schedule push trigger : jobId = " + realTimeTask.getId() );

                                    // 2、fresh next
                                    refreshNextValidTime(realTimeTask, System.currentTimeMillis());

                                    // next-trigger-time in 5s, pre-read again
                                    if (realTimeTask.getEnable()==TaskEnableEnum.TASK_ENABLE && nowTime + PRE_READ_MS > realTimeTask.getNextTriggerTime()) {

                                        // 1、make ring second
                                        int ringSecond = (int)((realTimeTask.getNextTriggerTime()/1000)%60);

                                        // 2、push time ring
                                        pushTimeRing(ringSecond, realTimeTask.getId());

                                        // 3、fresh next
                                        refreshNextValidTime(realTimeTask, realTimeTask.getNextTriggerTime());

                                    }

                                } else {
                                    // 2.3、trigger-pre-read：time-ring trigger && make next-trigger-time

                                    // 1、make ring second
                                    int ringSecond = (int)((realTimeTask.getNextTriggerTime()/1000)%60);

                                    // 2、push time ring
                                    pushTimeRing(ringSecond, realTimeTask.getId());

                                    // 3、fresh next
                                    refreshNextValidTime(realTimeTask, realTimeTask.getNextTriggerTime());

                                }

                            }

                            // 3、update trigger info
                            for (RealTimeTaskEntity realTimeTask: scheduleList) {
                                realtimeTaskMapper.updateTaskTriggerInfo(realTimeTask);
                            }

                        } else {
                            preReadSuc = false;
                        }

                    } catch (Exception e) {
                        if (!scheduleThreadToStop) {
                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#scheduleThread error:{}", e);
                        }
                    }finally {

                        // commit
                        if (conn != null) {
                            try {
                                conn.commit();
                            } catch (Throwable e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                            try {
                                conn.setAutoCommit(connAutoCommit);
                            } catch (Throwable e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                            try {
                                conn.close();
                            } catch (Throwable e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }

                        // close PreparedStatement
                        if (null != preparedStatement) {
                            try {
                                preparedStatement.close();
                            } catch (Throwable e) {
                                if (!scheduleThreadToStop) {
                                    logger.error(e.getMessage(), e);
                                }
                            }
                        }
                    }
                    long cost = System.currentTimeMillis()-start;


                    // Wait seconds, align second
                    if (cost < 1000) {  // scan-overtime, not wait
                        try {
                            // pre-read period: success > scan each second; fail > skip this period;
                            TimeUnit.MILLISECONDS.sleep((preReadSuc?1000:PRE_READ_MS) - System.currentTimeMillis()%1000);
                        } catch (Throwable e) {
                            if (!scheduleThreadToStop) {
                                logger.error(e.getMessage(), e);
                            }
                        }
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper#scheduleThread stop");

            }
        });
        scheduleThread.setDaemon(true);
        scheduleThread.setName("xxl-job, admin JobScheduleHelper#scheduleThread");
        scheduleThread.start();

        ringThread  = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!ringThreadToStop){
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000-System.currentTimeMillis() % 1000);
                    } catch (Throwable e) {
                        if (!ringThreadToStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }

                    try {
                        List<String> ringItemData = new ArrayList<>();
                        int nowSecond = Calendar.getInstance().get(Calendar.SECOND);   // 避免处理耗时太长，跨过刻度，向前校验一个刻度；
                        for (int i = 0; i < 2; i++) {
                            List<String> tmpData = ringData.remove( (nowSecond+60-i)%60 );
                            if (tmpData != null) {
                                ringItemData.addAll(tmpData);
                            }
                        }

                        logger.debug(">>>>>>>>>>> xxl-job, time-ring beat : " + nowSecond + " = " + Arrays.asList(ringItemData) );
                        if (ringItemData.size() > 0) {
                            // TODO
                            // do trigger
                            for (String jobId: ringItemData) {
                                // do trigger
                                jobTriggerPoolHelper.trigger(jobId);
                            }
                            ringItemData.clear();
                        }
                    }catch (Throwable e) {
                        if (!ringThreadToStop) {
                            logger.error(">>>>>>>>>>> xxl-job, JobScheduleHelper#ringThread error:{}", e);
                        }
                    }
                }
            }
        });
        ringThread.setDaemon(true);
        ringThread.setName("xxl-job, admin JobScheduleHelper#ringThread");
        ringThread.start();
    }

    private void refreshNextValidTime(RealTimeTaskEntity realTimeTask, Long fromTime) {
        try {
            Long nextValidTime = getNextTriggerTime(realTimeTask.getScheduledConf(), fromTime);
            if (nextValidTime != TriggerEnum.NULL_NEXT_TRIGGER_TIME) {
                realTimeTask.setLastTriggerTime(realTimeTask.getNextTriggerTime());
                realTimeTask.setNextTriggerTime(nextValidTime);
            } else {
                // generateNextValidTime fail, stop job
                realTimeTask.setEnable(TaskEnableEnum.TASK_UNABLE);
                realTimeTask.setLastTriggerTime(realTimeTask.getNextTriggerTime());
                realTimeTask.setNextTriggerTime(TriggerEnum.NULL_NEXT_TRIGGER_TIME);
            }
        } catch (Throwable e) {
            logger.error("错位{}",e);
        }
    }


    private void pushTimeRing(int ringSecond, String jobId){
        // push async ring
        List<String> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<String>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);

        logger.debug(">>>>>>>>>>> xxl-job, schedule push time-ring : " + ringSecond + " = " + Arrays.asList(ringItemData) );
    }

    public void toStop(){

        // 1、stop schedule
        scheduleThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);  // wait
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
        if (scheduleThread.getState() != Thread.State.TERMINATED){
            // interrupt and wait
            scheduleThread.interrupt();
            try {
                scheduleThread.join();
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }

        // if has ring data
        boolean hasRingData = false;
        if (!ringData.isEmpty()) {
            for (int second : ringData.keySet()) {
                List<String> tmpData = ringData.get(second);
                if (tmpData!=null && tmpData.size()>0) {
                    hasRingData = true;
                    break;
                }
            }
        }
        if (hasRingData) {
            try {
                TimeUnit.SECONDS.sleep(8);
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }

        // stop ring (wait job-in-memory stop)
        ringThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
        if (ringThread.getState() != Thread.State.TERMINATED){
            // interrupt and wait
            ringThread.interrupt();
            try {
                ringThread.join();
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }

        logger.info(">>>>>>>>>>> xxl-job, JobScheduleHelper stop");
    }


    // ---------------------- tools ----------------------
    private long getNextTriggerTime(String cronExpr, long fromTime) throws ParseException {
        CronExpression cron = new CronExpression(cronExpr);
        Date next = cron.getNextValidTimeAfter(new Date(fromTime));
        return next != null ? next.getTime() : TriggerEnum.NULL_NEXT_TRIGGER_TIME;
    }
}
