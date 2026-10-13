// Service layer: creates loan notes via the downstream Loan Servicing API.
package com.example.lending.loandata.service;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.CreateNoteRequest;
import com.example.lending.loandata.model.dto.CreateNoteResponse;
import com.example.lending.loandata.observability.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class LoanNotesService {

  private final LoanServicingClient loanServicingClient;

  public LoanNotesService(LoanServicingClient loanServicingClient) {
    this.loanServicingClient = loanServicingClient;
  }

  public CreateNoteResponse createNote(
      String loanNumber, String idempotencyKey, CreateNoteRequest request) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (correlationId == null) correlationId = "";
    // Generate idempotency key if caller did not provide one.
    String effectiveIdempotencyKey =
        (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : UUID.randomUUID().toString();
    return loanServicingClient.createNote(
        loanNumber, correlationId, effectiveIdempotencyKey, request);
  }
}
