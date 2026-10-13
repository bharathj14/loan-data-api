// Model layer: JPA entity for the customer table in AlloyDB.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA mapping for the {@code loan_data.customer} table in AlloyDB. Read-only — no write operations.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer", schema = "loan_data")
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

  @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
  private List<CustomerAccountRelationshipEntity> relationships = new ArrayList<>();
}
