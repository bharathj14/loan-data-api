package com.example.lending.loandata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lending.loandata.exception.CustomerNotFoundException;
import com.example.lending.loandata.exception.DatastoreException;
import com.example.lending.loandata.mapper.RelationshipAccountMapper;
import com.example.lending.loandata.model.dto.AccountDto;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import com.example.lending.loandata.model.dto.PageMeta;
import com.example.lending.loandata.model.dto.RelationshipAccountsResponse;
import com.example.lending.loandata.model.entity.AccountEntity;
import com.example.lending.loandata.model.entity.AccountType;
import com.example.lending.loandata.model.entity.CustomerAccountRelationshipEntity;
import com.example.lending.loandata.model.entity.RelationshipType;
import com.example.lending.loandata.repository.alloydb.CustomerAccountRelationshipRepository;
import com.example.lending.loandata.repository.alloydb.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RelationshipAccountServiceTest {

  @Mock private CustomerRepository customerRepository;
  @Mock private CustomerAccountRelationshipRepository relationshipRepository;
  @Mock private LoanService loanService;
  @Mock private RelationshipAccountMapper mapper;

  // Use a direct executor so CompletableFuture tasks run synchronously in tests
  private final Executor syncExecutor = Runnable::run;

  private RelationshipAccountService service;

  @BeforeEach
  void setUp() {
    service =
        new RelationshipAccountService(
            customerRepository, relationshipRepository, loanService, mapper, syncExecutor, 5000L);
  }

  @Test
  void getRelationshipAccounts_customerNotFound_throwsCustomerNotFoundException() {
    when(customerRepository.existsById("UNKNOWN")).thenReturn(false);

    assertThatThrownBy(() -> service.getRelationshipAccounts("UNKNOWN", null, false, 0, 20))
        .isInstanceOf(CustomerNotFoundException.class);
  }

  @Test
  void getRelationshipAccounts_withoutLoanDetails_returnsPagedResults() {
    when(customerRepository.existsById("CUST-0001")).thenReturn(true);
    Page<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    when(relationshipRepository.findByCustomerIdWithAccount(eq("CUST-0001"), any(), any()))
        .thenReturn(page);
    RelationshipAccountsResponse expected =
        new RelationshipAccountsResponse("CUST-0001", List.of(), new PageMeta(0, 20, 0, 0));
    when(mapper.toResponse(eq("CUST-0001"), eq(page), eq(Map.of()))).thenReturn(expected);

    RelationshipAccountsResponse result =
        service.getRelationshipAccounts("CUST-0001", null, false, 0, 20);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void getRelationshipAccounts_includeLoanDetails_fetchesSummariesInParallel() {
    when(customerRepository.existsById("CUST-0001")).thenReturn(true);

    CustomerAccountRelationshipEntity rel = buildLoanRelationship("LN100001", "CUST-0001");
    Page<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(rel), PageRequest.of(0, 20), 1);
    when(relationshipRepository.findByCustomerIdWithAccount(eq("CUST-0001"), any(), any()))
        .thenReturn(page);

    LoanSummaryDto summary =
        new LoanSummaryDto("LN100001", "PERSONAL_LOAN", "ACTIVE", new BigDecimal("5000"), "USD");
    when(loanService.getLoanSummary("LN100001")).thenReturn(Optional.of(summary));

    AccountDto accountDto =
        new AccountDto(
            "LN100001", "LOAN", "PERSONAL_LOAN", "ACTIVE", null, "USD", "PRIMARY", summary);
    RelationshipAccountsResponse expected =
        new RelationshipAccountsResponse(
            "CUST-0001", List.of(accountDto), new PageMeta(0, 20, 1, 1));
    when(mapper.toResponse(eq("CUST-0001"), eq(page), any())).thenReturn(expected);

    RelationshipAccountsResponse result =
        service.getRelationshipAccounts("CUST-0001", null, true, 0, 20);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void getRelationshipAccounts_bigtableFetchFails_throwsDatastoreException() {
    when(customerRepository.existsById("CUST-0001")).thenReturn(true);

    CustomerAccountRelationshipEntity rel = buildLoanRelationship("LN100001", "CUST-0001");
    Page<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(rel), PageRequest.of(0, 20), 1);
    when(relationshipRepository.findByCustomerIdWithAccount(eq("CUST-0001"), any(), any()))
        .thenReturn(page);

    when(loanService.getLoanSummary("LN100001"))
        .thenThrow(new DatastoreException("Bigtable error"));

    assertThatThrownBy(() -> service.getRelationshipAccounts("CUST-0001", null, true, 0, 20))
        .isInstanceOf(DatastoreException.class);
  }

  @Test
  void getRelationshipAccounts_noLoanAccounts_doesNotCallBigtable() {
    when(customerRepository.existsById("CUST-0001")).thenReturn(true);

    // Page with only a DEPOSIT account — no Bigtable calls expected
    CustomerAccountRelationshipEntity rel = buildDepositRelationship("DEP200001", "CUST-0001");
    Page<CustomerAccountRelationshipEntity> page =
        new PageImpl<>(List.of(rel), PageRequest.of(0, 20), 1);
    when(relationshipRepository.findByCustomerIdWithAccount(eq("CUST-0001"), any(), any()))
        .thenReturn(page);

    RelationshipAccountsResponse expected =
        new RelationshipAccountsResponse("CUST-0001", List.of(), new PageMeta(0, 20, 1, 1));
    when(mapper.toResponse(eq("CUST-0001"), eq(page), eq(Map.of()))).thenReturn(expected);

    RelationshipAccountsResponse result =
        service.getRelationshipAccounts("CUST-0001", null, true, 0, 20);

    assertThat(result).isEqualTo(expected);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private CustomerAccountRelationshipEntity buildLoanRelationship(
      String accountNumber, String customerId) {
    AccountEntity account = mock(AccountEntity.class);
    when(account.getAccountType()).thenReturn(AccountType.LOAN);
    // Extra fields only accessed by the mapper layer (not the service under test)
    lenient().when(account.getAccountNumber()).thenReturn(accountNumber);

    CustomerAccountRelationshipEntity rel = mock(CustomerAccountRelationshipEntity.class);
    when(rel.getAccountNumber()).thenReturn(accountNumber);
    when(rel.getAccount()).thenReturn(account);
    lenient().when(rel.getCustomerId()).thenReturn(customerId);
    lenient().when(rel.getRelationshipType()).thenReturn(RelationshipType.PRIMARY);
    return rel;
  }

  private CustomerAccountRelationshipEntity buildDepositRelationship(
      String accountNumber, String customerId) {
    AccountEntity account = mock(AccountEntity.class);
    when(account.getAccountType()).thenReturn(AccountType.DEPOSIT);
    lenient().when(account.getAccountNumber()).thenReturn(accountNumber);

    CustomerAccountRelationshipEntity rel = mock(CustomerAccountRelationshipEntity.class);
    lenient().when(rel.getAccountNumber()).thenReturn(accountNumber);
    when(rel.getAccount()).thenReturn(account);
    lenient().when(rel.getCustomerId()).thenReturn(customerId);
    lenient().when(rel.getRelationshipType()).thenReturn(RelationshipType.PRIMARY);
    return rel;
  }
}
