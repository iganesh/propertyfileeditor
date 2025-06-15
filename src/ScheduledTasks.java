package com.example.demo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ScheduledTasks {

    // Method 1: Runs every 5 minutes
    @Scheduled(fixedRate = 5_000) // 300,000 ms = 5 minutes
    @Async("taskExecutor")
    public void performTaskOne() {
        Thread.ofVirtual().start(() -> {
            try {
                System.out.println("Task 1 running on virtual thread: " + Thread.currentThread());
                // Add your task logic here (e.g., data processing)
                Thread.sleep(1000); // Simulate work
                System.out.println("Task 1 completed");
            } catch (Exception e) {
                System.err.println("Task 1 failed: " + e.getMessage());
            }
        });
    }

    // Method 2: Runs every 5 minutes
    @Scheduled(fixedRate = 5_000)
    @Async("taskExecutor")
    // 300,000 ms = 5 minutes
    public void performTaskTwo() {
        Thread.ofVirtual().start(() -> {
            try {
                System.out.println("Task 2 running on virtual thread: " + Thread.currentThread());
                // Add your task logic here (e.g., API call)
                Thread.sleep(10000); // Simulate work
                System.out.println("Task 2 completed");
            } catch (Exception e) {
                System.err.println("Task 2 failed: " + e.getMessage());
            }
        });
    }
}


package com.example.demo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadFactory(Thread.ofVirtual().factory());
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }
}

spring.application.name=demo
management.endpoints.web.exposure.include=health,info,metrics,beans,threaddump,scheduledtasks
spring.devtools.restart.enabled=true
spring.devtools.livereload.enabled=true
management.endpoints.web.base-path=/actuator

