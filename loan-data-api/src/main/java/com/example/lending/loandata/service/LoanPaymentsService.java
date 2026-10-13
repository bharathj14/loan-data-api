// Service layer: fetches loan payment history from the downstream Loan Servicing API.
package com.example.lending.loandata.service;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.PaymentHistoryResponse;
import com.example.lending.loandata.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class LoanPaymentsService {

  private final LoanServicingClient loanServicingClient;

  public LoanPaymentsService(LoanServicingClient loanServicingClient) {
    this.loanServicingClient = loanServicingClient;
  }

  public PaymentHistoryResponse getPayments(String loanNumber) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (correlationId == null) correlationId = "";
    return loanServicingClient.getPayments(loanNumber, correlationId);
  }
}
