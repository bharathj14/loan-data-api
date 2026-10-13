package com.example.lending.loandata.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

  private final MdcTaskDecorator decorator = new MdcTaskDecorator();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void decorate_propagatesMdcContextToRunnable() {
    MDC.put("correlationId", "abc-123");
    AtomicReference<String> capturedId = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> capturedId.set(MDC.get("correlationId")));
    MDC.clear();
    decorated.run();

    assertThat(capturedId.get()).isEqualTo("abc-123");
  }

  @Test
  void decorate_clearsMdcAfterRunnableCompletes() {
    MDC.put("correlationId", "abc-123");
    Runnable decorated = decorator.decorate(() -> {});
    MDC.clear();

    decorated.run();

    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void decorate_withEmptyMdcContext_doesNotSetMdc() {
    AtomicReference<String> capturedId = new AtomicReference<>();
    Runnable decorated = decorator.decorate(() -> capturedId.set(MDC.get("correlationId")));

    decorated.run();

    assertThat(capturedId.get()).isNull();
  }
}
