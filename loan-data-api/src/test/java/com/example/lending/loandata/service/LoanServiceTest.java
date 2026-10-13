package com.example.lending.loandata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.lending.loandata.exception.LoanNotFoundException;
import com.example.lending.loandata.mapper.LoanMapper;
import com.example.lending.loandata.model.dto.LoanDetailResponse;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import com.example.lending.loandata.repository.bigtable.BigtableRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

  @Mock private BigtableRepository bigtableRepository;
  @Mock private LoanMapper loanMapper;

  @InjectMocks private LoanService loanService;

  @Test
  void getLoanDetail_found_returnsMappedResponse() {
    Map<String, String> columns = Map.of("loan_number", "LN100001", "status", "ACTIVE");
    LoanDetailResponse expected =
        new LoanDetailResponse(
            "LN100001", null, "ACTIVE", null, null, null, null, null, null, null, null, null);

    when(bigtableRepository.findLoanRow("LN100001")).thenReturn(Optional.of(columns));
    when(loanMapper.toDetailResponse(columns)).thenReturn(expected);

    LoanDetailResponse result = loanService.getLoanDetail("LN100001");

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void getLoanDetail_notFound_throwsLoanNotFoundException() {
    when(bigtableRepository.findLoanRow("UNKNOWN")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> loanService.getLoanDetail("UNKNOWN"))
        .isInstanceOf(LoanNotFoundException.class);
  }

  @Test
  void getLoanSummary_found_returnsMappedSummary() {
    Map<String, String> columns =
        Map.of("loan_number", "LN100001", "outstanding_balance", "5000.00");
    LoanSummaryDto summary =
        new LoanSummaryDto("LN100001", null, null, new BigDecimal("5000.00"), null);

    when(bigtableRepository.findLoanSummaryRow("LN100001")).thenReturn(Optional.of(columns));
    when(loanMapper.toSummaryDto(columns)).thenReturn(summary);

    Optional<LoanSummaryDto> result = loanService.getLoanSummary("LN100001");

    assertThat(result).contains(summary);
  }

  @Test
  void getLoanSummary_notFound_returnsEmpty() {
    when(bigtableRepository.findLoanSummaryRow("UNKNOWN")).thenReturn(Optional.empty());

    assertThat(loanService.getLoanSummary("UNKNOWN")).isEmpty();
  }
}
