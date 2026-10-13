// Observability layer: utility for masking PII in log messages per the security policy.
package com.example.lending.loandata.observability;

public final class LogMask {

  private LogMask() {}

  /**
   * Masks a name: keeps the first character, replaces the rest with ***. Returns "***" if the name
   * is null or blank.
   */
  public static String name(String value) {
    if (value == null || value.isBlank()) return "***";
    return value.charAt(0) + "***";
  }

  /** Masks a monetary amount: always returns "[AMOUNT REDACTED]". */
  public static String amount(String value) {
    return "[AMOUNT REDACTED]";
  }

  /**
   * Masks an account or loan number: keeps only the last 4 characters. Returns "****" if the value
   * is null or has fewer than 4 characters.
   */
  public static String accountOrLoanNumber(String value) {
    if (value == null || value.length() < 4) return "****";
    return "****" + value.substring(value.length() - 4);
  }
}
