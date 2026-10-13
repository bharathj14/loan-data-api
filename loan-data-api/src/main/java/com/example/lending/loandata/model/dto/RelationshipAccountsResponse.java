package com.example.lending.loandata.model.dto;

import java.util.List;

/** Paginated response for {@code GET /api/v1/customers/{customerId}/relationship-accounts}. */
public record RelationshipAccountsResponse(
    String customerId, List<AccountDto> accounts, PageMeta page) {}
