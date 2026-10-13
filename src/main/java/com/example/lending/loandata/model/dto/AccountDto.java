package com.example.lending.loandata.model.dto;

import java.time.LocalDate;

/** DTO representing one account in a relationship-accounts page result. */
public record AccountDto(
    String accountNumber,
    String accountType,
    String productCode,
    String status,
    LocalDate openedDate,
    String currency,
    String relationshipType,
    /** Non-null only when {@code includeLoanDetails=true} and the account type is LOAN. */
    LoanSummaryDto loanDetails) {}
