// Repository layer: Spring Data JPA access for customer-account relationship data in AlloyDB.
package com.example.lending.loandata.repository.alloydb;

import com.example.lending.loandata.model.entity.CustomerAccountRelationshipEntity;
import com.example.lending.loandata.model.entity.RelationshipType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for customer-account relationship queries. Read-only. */
@Repository
public interface CustomerAccountRelationshipRepository
    extends JpaRepository<CustomerAccountRelationshipEntity, CustomerAccountRelationshipEntity.Id> {

  /**
   * Returns all relationship rows for the given customer, eagerly fetching the associated account,
   * optionally filtered by relationship type. Sorted by {@code account_number} for stable paging.
   */
  @Query(
      "SELECT r FROM CustomerAccountRelationshipEntity r "
          + "JOIN FETCH r.account "
          + "WHERE r.id.customerId = :customerId "
          + "AND (:relationshipType IS NULL OR r.id.relationshipType = :relationshipType) "
          + "ORDER BY r.id.accountNumber ASC")
  Page<CustomerAccountRelationshipEntity> findByCustomerIdWithAccount(
      @Param("customerId") String customerId,
      @Param("relationshipType") RelationshipType relationshipType,
      Pageable pageable);
}
