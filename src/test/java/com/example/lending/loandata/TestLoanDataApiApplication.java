package com.example.lending.loandata;

import org.springframework.boot.SpringApplication;

public class TestLoanDataApiApplication {

  public static void main(String[] args) {
    SpringApplication.from(LoanDataApiApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
