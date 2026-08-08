package com.safekeep.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * Dedicated thread pool for async email sending.
     * Keeps email tasks off the main request thread so the /register endpoint
     * returns immediately, even if Brevo is slow.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }

    /**
     * RestTemplate with explicit timeouts via SimpleClientHttpRequestFactory.
     * Without these, a slow/unreachable Brevo server will make the async thread
     * hang indefinitely. 10s connect + 15s read is generous but bounded.
     * Note: RestTemplateBuilder.connectTimeout(Duration) was removed in Spring Boot 3.x.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000); // 10 seconds
        factory.setReadTimeout(15_000);    // 15 seconds
        return new RestTemplate(factory);
    }
}
