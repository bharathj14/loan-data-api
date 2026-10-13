// Test layer: unit tests for LogMask PII-masking utility.
package com.example.lending.loandata.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskTest {

  @Test
  void name_null_returns3Stars() {
    assertThat(LogMask.name(null)).isEqualTo("***");
  }

  @Test
  void name_blank_returns3Stars() {
    assertThat(LogMask.name("")).isEqualTo("***");
  }

  @Test
  void name_whitespaceOnly_returns3Stars() {
    assertThat(LogMask.name("   ")).isEqualTo("***");
  }

  @Test
  void name_singleChar_returnsMasked() {
    assertThat(LogMask.name("J")).isEqualTo("J***");
  }

  @Test
  void name_multipleChars_returnsFirstCharPlusMask() {
    assertThat(LogMask.name("John")).isEqualTo("J***");
  }

  @Test
  void amount_anyValue_returnsRedacted() {
    assertThat(LogMask.amount("5000.00")).isEqualTo("[AMOUNT REDACTED]");
  }

  @Test
  void amount_null_returnsRedacted() {
    assertThat(LogMask.amount(null)).isEqualTo("[AMOUNT REDACTED]");
  }

  @Test
  void accountOrLoanNumber_null_returns4Stars() {
    assertThat(LogMask.accountOrLoanNumber(null)).isEqualTo("****");
  }

  @Test
  void accountOrLoanNumber_lessThan4Chars_returns4Stars() {
    assertThat(LogMask.accountOrLoanNumber("AB")).isEqualTo("****");
  }

  @Test
  void accountOrLoanNumber_exactly4Chars_returnsLast4() {
    assertThat(LogMask.accountOrLoanNumber("1234")).isEqualTo("****1234");
  }

  @Test
  void accountOrLoanNumber_longLoanNumber_returnsLast4Only() {
    assertThat(LogMask.accountOrLoanNumber("LN1000000001")).isEqualTo("****0001");
  }
}
