package com.example.lending.loandata.exception;

/** Thrown when a Bigtable row for the requested loan number does not exist. */
public class LoanNotFoundException extends RuntimeException {

  public LoanNotFoundException(String loanNumber) {
    super("Loan not found: " + loanNumber);
  }
}
