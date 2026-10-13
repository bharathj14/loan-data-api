package com.example.lending.loandata.exception;

/** Thrown when no customer record exists for the requested customer ID. */
public class CustomerNotFoundException extends RuntimeException {

  public CustomerNotFoundException(String customerId) {
    super("Customer not found: " + customerId);
  }
}
