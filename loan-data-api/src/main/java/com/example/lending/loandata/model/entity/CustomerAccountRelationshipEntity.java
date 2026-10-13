package com.example.lending.loandata.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** JPA mapping for the {@code customer_account_relationship} join table in AlloyDB. Read-only. */
@Entity
@Table(name = "customer_account_relationship")
@IdClass(CustomerAccountRelationshipId.class)
public class CustomerAccountRelationshipEntity {

  @Id
  @Column(name = "customer_id", nullable = false, length = 50)
  private String customerId;

  @Id
  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_type", nullable = false, length = 30)
  private RelationshipType relationshipType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_number", insertable = false, updatable = false)
  private AccountEntity account;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  protected CustomerAccountRelationshipEntity() {}

  public String getCustomerId() {
    return customerId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public RelationshipType getRelationshipType() {
    return relationshipType;
  }

  public AccountEntity getAccount() {
    return account;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }
}
