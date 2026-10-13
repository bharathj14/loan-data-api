package com.example.lending.loandata.config;

import com.example.lending.loandata.observability.MdcTaskDecorator;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Creates the bounded {@link Executor} used to fan-out parallel Bigtable lookups. */
@Configuration
public class ThreadPoolConfig {

  @Bean(name = "bigtableExecutor")
  public Executor bigtableExecutor(AppProperties properties) {
    AppProperties.Executor cfg = properties.executor();
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(cfg.corePoolSize());
    executor.setMaxPoolSize(cfg.maxPoolSize());
    executor.setQueueCapacity(cfg.queueCapacity());
    executor.setThreadNamePrefix(cfg.threadNamePrefix());
    executor.setTaskDecorator(new MdcTaskDecorator());
    executor.initialize();
    return executor;
  }
}
