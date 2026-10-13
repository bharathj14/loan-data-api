package com.example.lending.loandata.mapper;

import com.example.lending.loandata.model.dto.LoanDetailResponse;
import com.example.lending.loandata.model.dto.LoanSummaryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converts a raw Bigtable column map (qualifier → value strings) into typed DTOs. Parsing errors on
 * individual columns are silently absorbed — a {@code null} field is preferable to a 500 caused by
 * an unexpected empty cell.
 */
@Component
public class LoanMapper {

  public LoanDetailResponse toDetailResponse(Map<String, String> columns) {
    return new LoanDetailResponse(
        columns.get("loan_number"),
        columns.get("product_type"),
        columns.get("status"),
        parseBigDecimal(columns.get("principal_amount")),
        parseBigDecimal(columns.get("outstanding_balance")),
        parseBigDecimal(columns.get("interest_rate")),
        columns.get("currency"),
        parseDate(columns.get("origination_date")),
        parseDate(columns.get("maturity_date")),
        parseDate(columns.get("next_payment_due_date")),
        parseBigDecimal(columns.get("next_payment_amount")),
        columns.get("borrower_customer_id"));
  }

  public LoanSummaryDto toSummaryDto(Map<String, String> columns) {
    return new LoanSummaryDto(
        columns.get("loan_number"),
        columns.get("product_type"),
        columns.get("status"),
        parseBigDecimal(columns.get("outstanding_balance")),
        columns.get("currency"));
  }

  private static BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (Exception e) {
      return null;
    }
  }
}
