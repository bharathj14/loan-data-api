package com.example.lending.loandata.mapper;

import com.example.lending.loandata.model.dto.AccountDto;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import com.example.lending.loandata.model.dto.PageMeta;
import com.example.lending.loandata.model.dto.RelationshipAccountsResponse;
import com.example.lending.loandata.model.entity.CustomerAccountRelationshipEntity;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Converts JPA entities and Bigtable summaries into response DTOs. */
@Component
public class RelationshipAccountMapper {

  /**
   * Builds the full paginated response. {@code loanSummaries} maps account number to its Bigtable
   * loan summary; may be empty when {@code includeLoanDetails=false}.
   */
  public RelationshipAccountsResponse toResponse(
      String customerId,
      Page<CustomerAccountRelationshipEntity> page,
      Map<String, LoanSummaryDto> loanSummaries) {

    List<AccountDto> accounts =
        page.getContent().stream()
            .map(rel -> toAccountDto(rel, loanSummaries.get(rel.getAccountNumber())))
            .toList();

    PageMeta pageMeta =
        new PageMeta(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());

    return new RelationshipAccountsResponse(customerId, accounts, pageMeta);
  }

  private AccountDto toAccountDto(
      CustomerAccountRelationshipEntity rel, LoanSummaryDto loanSummary) {
    return new AccountDto(
        rel.getAccountNumber(),
        rel.getAccount().getAccountType().name(),
        rel.getAccount().getProductCode(),
        rel.getAccount().getStatus(),
        rel.getAccount().getOpenedDate(),
        rel.getAccount().getCurrency(),
        rel.getRelationshipType().name(),
        loanSummary);
  }
}
