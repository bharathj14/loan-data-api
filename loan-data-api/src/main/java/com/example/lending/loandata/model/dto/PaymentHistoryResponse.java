// Model layer: DTO for the downstream GET /v1/loans/{loanNumber}/payments response.
package com.example.lending.loandata.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentHistoryResponse(
    String loanNumber,
    int totalPayments,
    LocalDate nextPaymentDueDate,
    BigDecimal nextPaymentAmount,
    List<Payment> payments) {

  public record Payment(
      int paymentNumber,
      LocalDate paymentDate,
      BigDecimal amount,
      String status,
      BigDecimal principalPaid,
      BigDecimal interestPaid,
      BigDecimal remainingBalance) {}
}
