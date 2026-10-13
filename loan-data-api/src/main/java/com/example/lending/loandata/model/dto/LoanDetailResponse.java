package com.example.lending.loandata.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for {@code GET /api/v1/loans/{loanNumber}}. All fields are nullable to allow partial
 * Bigtable rows (columns that have never been written return {@code null}).
 */
public record LoanDetailResponse(
    String loanNumber,
    String productType,
    String status,
    BigDecimal principalAmount,
    BigDecimal outstandingBalance,
    BigDecimal interestRate,
    String currency,
    LocalDate originationDate,
    LocalDate maturityDate,
    LocalDate nextPaymentDueDate,
    BigDecimal nextPaymentAmount,
    String borrowerCustomerId) {}
