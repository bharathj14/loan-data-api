// Model layer: JPA entity for the customer_account_relationship join table in AlloyDB.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** JPA mapping for the {@code loan_data.customer_account_relationship} join table. Read-only. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_account_relationship", schema = "loan_data")
public class CustomerAccountRelationshipEntity {

  @EmbeddedId private Id id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("customerId")
  @JoinColumn(name = "customer_id")
  private CustomerEntity customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("accountNumber")
  @JoinColumn(name = "account_number")
  private AccountEntity account;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  // Convenience delegates — keep mapper and service callers unaffected by the ID refactor.
  public String getCustomerId() {
    return id.customerId;
  }

  public String getAccountNumber() {
    return id.accountNumber;
  }

  public RelationshipType getRelationshipType() {
    return id.relationshipType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomerAccountRelationshipEntity other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  /** Composite primary key for {@link CustomerAccountRelationshipEntity}. */
  @Embeddable
  public static class Id implements Serializable {

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 30)
    private RelationshipType relationshipType;

    protected Id() {}

    public String getCustomerId() {
      return customerId;
    }

    public String getAccountNumber() {
      return accountNumber;
    }

    public RelationshipType getRelationshipType() {
      return relationshipType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Id other)) return false;
      return Objects.equals(customerId, other.customerId)
          && Objects.equals(accountNumber, other.accountNumber)
          && Objects.equals(relationshipType, other.relationshipType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(customerId, accountNumber, relationshipType);
    }
  }
}
