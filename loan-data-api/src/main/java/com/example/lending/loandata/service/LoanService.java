package com.example.lending.loandata.service;

import com.example.lending.loandata.exception.LoanNotFoundException;
import com.example.lending.loandata.mapper.LoanMapper;
import com.example.lending.loandata.model.dto.LoanDetailResponse;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import com.example.lending.loandata.repository.bigtable.BigtableRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Fetches loan data from Bigtable and maps it to DTOs. */
@Service
public class LoanService {

  private final BigtableRepository bigtableRepository;
  private final LoanMapper loanMapper;

  public LoanService(BigtableRepository bigtableRepository, LoanMapper loanMapper) {
    this.bigtableRepository = bigtableRepository;
    this.loanMapper = loanMapper;
  }

  /**
   * Returns full loan details for the given loan number.
   *
   * @throws LoanNotFoundException if no Bigtable row exists for the loan number.
   */
  public LoanDetailResponse getLoanDetail(String loanNumber) {
    Map<String, String> columns =
        bigtableRepository
            .findLoanRow(loanNumber)
            .orElseThrow(() -> new LoanNotFoundException(loanNumber));
    return loanMapper.toDetailResponse(columns);
  }

  /**
   * Returns a compact loan summary for the given loan number, or {@link Optional#empty()} if the
   * row is not found. Used internally by the relationship-accounts enrichment path.
   */
  public Optional<LoanSummaryDto> getLoanSummary(String loanNumber) {
    return bigtableRepository.findLoanSummaryRow(loanNumber).map(loanMapper::toSummaryDto);
  }
}
