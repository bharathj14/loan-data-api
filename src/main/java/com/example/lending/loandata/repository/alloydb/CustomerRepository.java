package com.example.lending.loandata.repository.alloydb;

import com.example.lending.loandata.model.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for customer existence checks. Read-only. */
@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {}
