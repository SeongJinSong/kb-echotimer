package com.kb.timer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄러 설정
 * 타이머 완료 스케줄링을 위한 TaskScheduler 빈 설정
 */
@Configuration
public class SchedulerConfig {

    /**
     * 타이머 완료 스케줄링용 TaskScheduler
     * 
     * @return TaskScheduler 빈
     */
    @Bean
    public TaskScheduler timerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // 동시에 처리할 수 있는 타이머 수
        scheduler.setThreadNamePrefix("timer-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
