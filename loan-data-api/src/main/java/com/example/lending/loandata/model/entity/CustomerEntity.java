package com.example.lending.loandata.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** JPA mapping for the {@code customer} table in AlloyDB. Read-only — no write operations. */
@Entity
@Table(name = "customer")
public class CustomerEntity {

  @Id
  @Column(name = "customer_id", nullable = false, length = 50)
  private String customerId;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "customer_type", nullable = false, length = 50)
  private String customerType;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected CustomerEntity() {}

  public String getCustomerId() {
    return customerId;
  }

  public String getFullName() {
    return fullName;
  }

  public String getCustomerType() {
    return customerType;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
