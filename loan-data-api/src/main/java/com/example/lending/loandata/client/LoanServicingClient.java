// Client layer: HTTP interface for the downstream Loan Servicing API.
package com.example.lending.loandata.client;

import com.example.lending.loandata.model.dto.CreateNoteRequest;
import com.example.lending.loandata.model.dto.CreateNoteResponse;
import com.example.lending.loandata.model.dto.PaymentHistoryResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/v1/loans")
public interface LoanServicingClient {

  @GetExchange("/{loanNumber}/payments")
  PaymentHistoryResponse getPayments(
      @PathVariable String loanNumber, @RequestHeader("X-Correlation-Id") String correlationId);

  @PostExchange("/{loanNumber}/notes")
  CreateNoteResponse createNote(
      @PathVariable String loanNumber,
      @RequestHeader("X-Correlation-Id") String correlationId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody CreateNoteRequest request);
}
