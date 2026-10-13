package com.example.lending.loandata.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** JPA mapping for the {@code account} table in AlloyDB. Read-only. */
@Entity
@Table(name = "account")
public class AccountEntity {

  @Id
  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 20)
  private AccountType accountType;

  @Column(name = "product_code", nullable = false, length = 50)
  private String productCode;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "opened_date")
  private LocalDate openedDate;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  protected AccountEntity() {}

  public String getAccountNumber() {
    return accountNumber;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public String getProductCode() {
    return productCode;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getOpenedDate() {
    return openedDate;
  }

  public String getCurrency() {
    return currency;
  }
}
