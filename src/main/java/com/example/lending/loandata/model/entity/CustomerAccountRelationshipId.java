package com.example.lending.loandata.model.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link CustomerAccountRelationshipEntity}. */
public class CustomerAccountRelationshipId implements Serializable {

  private String customerId;
  private String accountNumber;
  private RelationshipType relationshipType;

  protected CustomerAccountRelationshipId() {}

  public CustomerAccountRelationshipId(
      String customerId, String accountNumber, RelationshipType relationshipType) {
    this.customerId = customerId;
    this.accountNumber = accountNumber;
    this.relationshipType = relationshipType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomerAccountRelationshipId other)) return false;
    return Objects.equals(customerId, other.customerId)
        && Objects.equals(accountNumber, other.accountNumber)
        && Objects.equals(relationshipType, other.relationshipType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId, accountNumber, relationshipType);
  }
}
