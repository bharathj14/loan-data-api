// Model layer: JPA entity for the account table in AlloyDB.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** JPA mapping for the {@code loan_data.account} table in AlloyDB. Read-only. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "account", schema = "loan_data")
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
}
