package com.example.lending.loandata.model.dto;

import java.math.BigDecimal;

/**
 * Compact loan summary attached to relationship-account entries when {@code
 * includeLoanDetails=true} is requested. Fetched from Bigtable in parallel for LOAN-type accounts.
 */
public record LoanSummaryDto(
    String loanNumber,
    String productType,
    String status,
    BigDecimal outstandingBalance,
    String currency) {}
