// TEMPORARY: stub controller for the Loan Servicing API — local dev only, never deployed.
// Layer: controller in downstream-stub module.
package com.example.lending.stub;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/loans")
public class LoanServicingStubController {

  record PaymentHistoryResponse(String loanNumber, int totalPayments,
      LocalDate nextPaymentDueDate, BigDecimal nextPaymentAmount, List<Payment> payments) {}
  record Payment(int paymentNumber, LocalDate paymentDate, BigDecimal amount, String status,
      BigDecimal principalPaid, BigDecimal interestPaid, BigDecimal remainingBalance) {}
  record CreateNoteRequest(String noteType, String noteText, String createdBy) {}
  record CreateNoteResponse(String noteId, String loanNumber, String noteType,
      String noteText, String createdBy, OffsetDateTime createdAt) {}

  private static final Map<String, List<Payment>> PAYMENTS = Map.of(
      "LN1000000001", List.of(
          new Payment(1, LocalDate.of(2026, 10, 1), new BigDecimal("1200.00"), "PAID",
              new BigDecimal("800.00"), new BigDecimal("400.00"), new BigDecimal("149200.00")),
          new Payment(2, LocalDate.of(2026, 9, 1), new BigDecimal("1200.00"), "PAID",
              new BigDecimal("795.00"), new BigDecimal("405.00"), new BigDecimal("150000.00"))),
      "LN1000000002", List.of(
          new Payment(1, LocalDate.of(2026, 10, 1), new BigDecimal("2000.00"), "PAID",
              new BigDecimal("1400.00"), new BigDecimal("600.00"), new BigDecimal("248600.00"))),
      "LN1000000003", List.of(),
      "LN1000000004", List.of(
          new Payment(1, LocalDate.of(2026, 8, 1), new BigDecimal("600.00"), "PAID",
              new BigDecimal("450.00"), new BigDecimal("150.00"), new BigDecimal("49550.00"))),
      "LN1000000005", List.of(
          new Payment(1, LocalDate.of(2026, 10, 1), new BigDecimal("2400.00"), "PAID",
              new BigDecimal("1800.00"), new BigDecimal("600.00"), new BigDecimal("316800.00")))
  );

  @GetMapping("/{loanNumber}/payments")
  public ResponseEntity<PaymentHistoryResponse> getPayments(
      @PathVariable String loanNumber,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

    // Simulate downstream 500 for test scenarios
    if ("LN_ERROR_500".equals(loanNumber)) {
      return ResponseEntity.internalServerError().build();
    }

    List<Payment> payments = PAYMENTS.get(loanNumber);
    if (payments == null) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(new PaymentHistoryResponse(
        loanNumber, payments.size(),
        LocalDate.now().plusMonths(1),
        new BigDecimal("1200.00"),
        payments));
  }

  @PostMapping("/{loanNumber}/notes")
  public ResponseEntity<CreateNoteResponse> createNote(
      @PathVariable String loanNumber,
      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CreateNoteRequest request) {

    if ("LN_ERROR_500".equals(loanNumber)) {
      return ResponseEntity.internalServerError().build();
    }

    if (!PAYMENTS.containsKey(loanNumber)) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(new CreateNoteResponse(
        UUID.randomUUID().toString(),
        loanNumber,
        request.noteType(),
        request.noteText(),
        request.createdBy(),
        OffsetDateTime.now()));
  }
}
