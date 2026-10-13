package com.example.lending.loandata.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lending.loandata.model.dto.LoanDetailResponse;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoanMapperTest {

  private final LoanMapper mapper = new LoanMapper();

  @Test
  void toDetailResponse_mapsAllColumns() {
    Map<String, String> columns =
        Map.ofEntries(
            Map.entry("loan_number", "LN100001"),
            Map.entry("product_type", "PERSONAL_LOAN"),
            Map.entry("status", "ACTIVE"),
            Map.entry("principal_amount", "10000.00"),
            Map.entry("outstanding_balance", "8500.00"),
            Map.entry("interest_rate", "5.25"),
            Map.entry("currency", "USD"),
            Map.entry("origination_date", "2022-01-15"),
            Map.entry("maturity_date", "2027-01-15"),
            Map.entry("next_payment_due_date", "2024-02-01"),
            Map.entry("next_payment_amount", "250.00"),
            Map.entry("borrower_customer_id", "CUST-0001"));

    LoanDetailResponse response = mapper.toDetailResponse(columns);

    assertThat(response.loanNumber()).isEqualTo("LN100001");
    assertThat(response.productType()).isEqualTo("PERSONAL_LOAN");
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.principalAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    assertThat(response.outstandingBalance()).isEqualByComparingTo(new BigDecimal("8500.00"));
    assertThat(response.interestRate()).isEqualByComparingTo(new BigDecimal("5.25"));
    assertThat(response.currency()).isEqualTo("USD");
    assertThat(response.originationDate()).isEqualTo(LocalDate.of(2022, 1, 15));
    assertThat(response.maturityDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    assertThat(response.nextPaymentDueDate()).isEqualTo(LocalDate.of(2024, 2, 1));
    assertThat(response.nextPaymentAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
    assertThat(response.borrowerCustomerId()).isEqualTo("CUST-0001");
  }

  @Test
  void toDetailResponse_nullsOnMissingColumns() {
    LoanDetailResponse response = mapper.toDetailResponse(Map.of());

    assertThat(response.loanNumber()).isNull();
    assertThat(response.principalAmount()).isNull();
    assertThat(response.originationDate()).isNull();
  }

  @Test
  void toDetailResponse_nullsOnMalformedNumbers() {
    LoanDetailResponse response =
        mapper.toDetailResponse(Map.of("principal_amount", "not-a-number"));

    assertThat(response.principalAmount()).isNull();
  }

  @Test
  void toDetailResponse_nullsOnMalformedDates() {
    LoanDetailResponse response = mapper.toDetailResponse(Map.of("origination_date", "13/99/2022"));

    assertThat(response.originationDate()).isNull();
  }

  @Test
  void toSummaryDto_mapsRequiredFields() {
    Map<String, String> columns =
        Map.of(
            "loan_number", "LN100001",
            "product_type", "MORTGAGE",
            "status", "ACTIVE",
            "outstanding_balance", "200000.00",
            "currency", "USD");

    LoanSummaryDto summary = mapper.toSummaryDto(columns);

    assertThat(summary.loanNumber()).isEqualTo("LN100001");
    assertThat(summary.productType()).isEqualTo("MORTGAGE");
    assertThat(summary.status()).isEqualTo("ACTIVE");
    assertThat(summary.outstandingBalance()).isEqualByComparingTo(new BigDecimal("200000.00"));
    assertThat(summary.currency()).isEqualTo("USD");
  }
}
