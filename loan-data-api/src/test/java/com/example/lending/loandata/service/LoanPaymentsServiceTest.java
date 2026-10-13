// Tests for LoanPaymentsService — verifies correlation ID propagation and client delegation.
package com.example.lending.loandata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.PaymentHistoryResponse;
import com.example.lending.loandata.observability.CorrelationIdFilter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class LoanPaymentsServiceTest {

  @Mock LoanServicingClient loanServicingClient;
  @InjectMocks LoanPaymentsService service;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void getPayments_propagatesCorrelationId() {
    MDC.put(CorrelationIdFilter.MDC_KEY, "test-corr-id");
    PaymentHistoryResponse expected =
        new PaymentHistoryResponse("LN1000000001", 0, null, null, List.of());
    when(loanServicingClient.getPayments("LN1000000001", "test-corr-id")).thenReturn(expected);

    PaymentHistoryResponse result = service.getPayments("LN1000000001");

    assertThat(result).isEqualTo(expected);
    verify(loanServicingClient).getPayments("LN1000000001", "test-corr-id");
  }

  @Test
  void getPayments_usesEmptyCorrelationIdWhenMdcIsEmpty() {
    PaymentHistoryResponse expected =
        new PaymentHistoryResponse("LN1000000001", 0, null, null, List.of());
    when(loanServicingClient.getPayments("LN1000000001", "")).thenReturn(expected);

    PaymentHistoryResponse result = service.getPayments("LN1000000001");

    assertThat(result).isEqualTo(expected);
  }
}
