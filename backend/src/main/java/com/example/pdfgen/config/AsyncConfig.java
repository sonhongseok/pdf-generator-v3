// backend/src/main/java/com/example/pdfgen/config/AsyncConfig.java
package com.example.pdfgen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring @Async 및 @Scheduled 활성화 설정.
 *
 * MS Word COM 자동화는 다중 프로세스 동시 실행 시 충돌하므로,
 * PDF 생성 전용 스레드풀을 단일 스레드(corePoolSize=1, maxPoolSize=1)로 고정합니다.
 * 동시에 여러 요청이 들어와도 큐(queue)에서 순서대로 1건씩 처리합니다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    // PDF 생성 전용 스레드풀 빈 이름
    public static final String PDF_EXECUTOR = "pdfTaskExecutor";

    // 큐에 대기 가능한 최대 작업 수
    private static final int QUEUE_CAPACITY = 50;

    @Bean(name = PDF_EXECUTOR)
    public Executor pdfTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // MS Word COM 충돌 방지: 반드시 단일 스레드로 고정
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("pdf-gen-");
        executor.initialize();
        return executor;
    }
}
