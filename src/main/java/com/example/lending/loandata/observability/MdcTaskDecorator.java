package com.example.lending.loandata.observability;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Propagates the SLF4J MDC context map from the submitting thread into the worker thread. Without
 * this, log lines emitted by Bigtable parallel-fetch tasks would lose the correlation ID.
 */
public class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return () -> {
      try {
        if (contextMap != null) {
          MDC.setContextMap(contextMap);
        }
        runnable.run();
      } finally {
        MDC.clear();
      }
    };
  }
}
