package com.example.bookkeeping.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置类
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 给spring的@Async的任务创建一个默认的异步任务执行器
     * @return
     */
    @Override
    public Executor getAsyncExecutor() {
        /*创建线程池*/
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        /*核心线程数*/
        executor.setCorePoolSize(5);
        /*最大线程数*/
        executor.setMaxPoolSize(10);
        /*任务等待队列容量*/
        executor.setQueueCapacity(100);
        /*设置线程名前缀*/
        executor.setThreadNamePrefix("WxMsg-Async-");
        /*初始化线程*/
        executor.initialize();
        return executor;
    }
}
