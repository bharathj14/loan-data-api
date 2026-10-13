package com.example.lending.loandata.service;

import com.example.lending.loandata.exception.CustomerNotFoundException;
import com.example.lending.loandata.exception.DatastoreException;
import com.example.lending.loandata.mapper.RelationshipAccountMapper;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import com.example.lending.loandata.model.dto.RelationshipAccountsResponse;
import com.example.lending.loandata.model.entity.AccountType;
import com.example.lending.loandata.model.entity.CustomerAccountRelationshipEntity;
import com.example.lending.loandata.model.entity.RelationshipType;
import com.example.lending.loandata.repository.alloydb.CustomerAccountRelationshipRepository;
import com.example.lending.loandata.repository.alloydb.CustomerRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches customer relationship accounts from AlloyDB and optionally enriches LOAN-type accounts
 * with summaries from Bigtable.
 *
 * <p><b>Partial-failure policy:</b> when {@code includeLoanDetails=true} the Bigtable lookups run
 * in parallel. If any lookup fails or the entire batch exceeds {@code bigtableParallelTimeoutMs},
 * the whole request fails with 503. Partial results are never returned because returning accounts
 * with inconsistent loan detail enrichment would be misleading to API consumers. This decision is
 * documented in {@code docs/DECISIONS.md}.
 */
@Service
public class RelationshipAccountService {

  private final CustomerRepository customerRepository;
  private final CustomerAccountRelationshipRepository relationshipRepository;
  private final LoanService loanService;
  private final RelationshipAccountMapper mapper;
  private final Executor bigtableExecutor;
  private final long bigtableParallelTimeoutMs;

  public RelationshipAccountService(
      CustomerRepository customerRepository,
      CustomerAccountRelationshipRepository relationshipRepository,
      LoanService loanService,
      RelationshipAccountMapper mapper,
      @Qualifier("bigtableExecutor") Executor bigtableExecutor,
      @Value("${app.bigtable.read-timeout-ms:10000}") long bigtableParallelTimeoutMs) {
    this.customerRepository = customerRepository;
    this.relationshipRepository = relationshipRepository;
    this.loanService = loanService;
    this.mapper = mapper;
    this.bigtableExecutor = bigtableExecutor;
    this.bigtableParallelTimeoutMs = bigtableParallelTimeoutMs;
  }

  /**
   * Returns paginated relationship accounts for a customer.
   *
   * @throws CustomerNotFoundException if the customer does not exist.
   * @throws DatastoreException (503) if {@code includeLoanDetails=true} and any Bigtable fetch
   *     fails or times out.
   */
  @Transactional(readOnly = true)
  public RelationshipAccountsResponse getRelationshipAccounts(
      String customerId,
      RelationshipType relationshipType,
      boolean includeLoanDetails,
      int page,
      int size) {

    if (!customerRepository.existsById(customerId)) {
      throw new CustomerNotFoundException(customerId);
    }

    Pageable pageable = PageRequest.of(page, size);
    Page<CustomerAccountRelationshipEntity> resultPage =
        relationshipRepository.findByCustomerIdWithAccount(customerId, relationshipType, pageable);

    Map<String, LoanSummaryDto> loanSummaries =
        includeLoanDetails ? fetchLoanSummariesInParallel(resultPage.getContent()) : Map.of();

    return mapper.toResponse(customerId, resultPage, loanSummaries);
  }

  /**
   * Fans out Bigtable reads for all LOAN-type accounts on the current page using the bounded
   * executor, then collects results with a wall-clock timeout. Any exception or timeout causes the
   * entire request to fail with a {@link DatastoreException} (mapped to 503).
   */
  private Map<String, LoanSummaryDto> fetchLoanSummariesInParallel(
      List<CustomerAccountRelationshipEntity> relationships) {

    List<String> loanAccountNumbers =
        relationships.stream()
            .filter(r -> r.getAccount().getAccountType() == AccountType.LOAN)
            .map(CustomerAccountRelationshipEntity::getAccountNumber)
            .distinct()
            .toList();

    if (loanAccountNumbers.isEmpty()) {
      return Map.of();
    }

    List<CompletableFuture<Optional<LoanSummaryDto>>> futures =
        loanAccountNumbers.stream()
            .map(
                accNum ->
                    CompletableFuture.supplyAsync(
                        () -> loanService.getLoanSummary(accNum), bigtableExecutor))
            .toList();

    CompletableFuture<Void> all =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      all.get(bigtableParallelTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      futures.forEach(f -> f.cancel(true));
      throw new DatastoreException("Bigtable parallel fetch timed out");
    } catch (ExecutionException e) {
      throw new DatastoreException("Bigtable parallel fetch failed", e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DatastoreException("Bigtable parallel fetch interrupted");
    }

    Map<String, LoanSummaryDto> result = new HashMap<>();
    for (int i = 0; i < loanAccountNumbers.size(); i++) {
      futures.get(i).join().ifPresent(summary -> result.put(summary.loanNumber(), summary));
    }
    return result;
  }
}
