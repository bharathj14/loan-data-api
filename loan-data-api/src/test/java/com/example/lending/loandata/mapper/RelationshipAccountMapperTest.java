package com.example.lending.loandata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lending.loandata.model.dto.AccountDto;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import com.example.lending.loandata.model.dto.RelationshipAccountsResponse;
import com.example.lending.loandata.model.entity.AccountEntity;
import com.example.lending.loandata.model.entity.AccountType;
import com.example.lending.loandata.model.entity.CustomerAccountRelationshipEntity;
import com.example.lending.loandata.model.entity.RelationshipType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class RelationshipAccountMapperTest {

  private final RelationshipAccountMapper mapper = new RelationshipAccountMapper();

  @Test
  void toResponse_withoutLoanDetails_setsLoanDetailsNull() {
    CustomerAccountRelationshipEntity rel = buildLoanRelationship("LN100001", "CUST-0001");
    PageImpl<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(rel), PageRequest.of(0, 20), 1);

    RelationshipAccountsResponse response = mapper.toResponse("CUST-0001", page, Map.of());

    assertThat(response.customerId()).isEqualTo("CUST-0001");
    assertThat(response.accounts()).hasSize(1);
    AccountDto account = response.accounts().get(0);
    assertThat(account.accountNumber()).isEqualTo("LN100001");
    assertThat(account.loanDetails()).isNull();
    assertThat(response.page().totalElements()).isEqualTo(1);
    assertThat(response.page().number()).isEqualTo(0);
  }

  @Test
  void toResponse_withLoanDetails_attachesSummary() {
    CustomerAccountRelationshipEntity rel = buildLoanRelationship("LN100001", "CUST-0001");
    PageImpl<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(rel), PageRequest.of(0, 20), 1);
    LoanSummaryDto summary =
        new LoanSummaryDto("LN100001", "PERSONAL_LOAN", "ACTIVE", new BigDecimal("8500"), "USD");

    RelationshipAccountsResponse response =
        mapper.toResponse("CUST-0001", page, Map.of("LN100001", summary));

    AccountDto account = response.accounts().get(0);
    assertThat(account.loanDetails()).isEqualTo(summary);
  }

  @Test
  void toResponse_pageMetaReflectsPage() {
    PageImpl<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(), PageRequest.of(2, 10), 35);

    RelationshipAccountsResponse response = mapper.toResponse("C1", page, Map.of());

    assertThat(response.page().number()).isEqualTo(2);
    assertThat(response.page().size()).isEqualTo(10);
    assertThat(response.page().totalElements()).isEqualTo(35);
    assertThat(response.page().totalPages()).isEqualTo(4);
  }

  private CustomerAccountRelationshipEntity buildLoanRelationship(
      String accountNumber, String customerId) {
    AccountEntity account = mock(AccountEntity.class);
    when(account.getAccountNumber()).thenReturn(accountNumber);
    when(account.getAccountType()).thenReturn(AccountType.LOAN);
    when(account.getProductCode()).thenReturn("PERSONAL_LOAN");
    when(account.getStatus()).thenReturn("ACTIVE");
    when(account.getOpenedDate()).thenReturn(LocalDate.of(2022, 1, 15));
    when(account.getCurrency()).thenReturn("USD");

    CustomerAccountRelationshipEntity rel = mock(CustomerAccountRelationshipEntity.class);
    when(rel.getCustomerId()).thenReturn(customerId);
    when(rel.getAccountNumber()).thenReturn(accountNumber);
    when(rel.getRelationshipType()).thenReturn(RelationshipType.PRIMARY);
    when(rel.getAccount()).thenReturn(account);
    return rel;
  }
}
