package com.example.lending.loandata.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.remove(CorrelationIdFilter.MDC_KEY);
  }

  @Test
  void doFilter_existingCorrelationIdHeader_propagatedToResponseAndMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER_NAME, "existing-id-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringFilter = new AtomicReference<>();

    filter.doFilter(
        request, response, (req, res) -> mdcDuringFilter.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

    assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("existing-id-123");
    assertThat(mdcDuringFilter.get()).isEqualTo("existing-id-123");
  }

  @Test
  void doFilter_missingHeader_generatesUuidAndSetsResponseHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String id = response.getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(id).isNotNull();
    assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void doFilter_clearsMdcAfterRequestCompletes() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    MDC.put(CorrelationIdFilter.MDC_KEY, "pre-existing");

    filter.doFilter(request, response, chain);

    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }
}
