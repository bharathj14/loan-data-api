package com.example.lending.loandata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.loandata.exception.CustomerNotFoundException;
import com.example.lending.loandata.exception.DatastoreException;
import com.example.lending.loandata.exception.GlobalExceptionHandler;
import com.example.lending.loandata.model.dto.AccountDto;
import com.example.lending.loandata.model.dto.PageMeta;
import com.example.lending.loandata.model.dto.RelationshipAccountsResponse;
import com.example.lending.loandata.service.RelationshipAccountService;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RelationshipAccountControllerTest {

  @Mock private RelationshipAccountService relationshipAccountService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new RelationshipAccountController(relationshipAccountService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getRelationshipAccounts_found_returns200() throws Exception {
    AccountDto account =
        new AccountDto("LN100001", "LOAN", "PERSONAL_LOAN", "ACTIVE", null, "USD", "PRIMARY", null);
    RelationshipAccountsResponse response =
        new RelationshipAccountsResponse("CUST-0001", List.of(account), new PageMeta(0, 20, 1, 1));

    when(relationshipAccountService.getRelationshipAccounts(
            eq("CUST-0001"), any(), eq(false), eq(0), eq(20)))
        .thenReturn(response);

    mockMvc
        .perform(get("/api/v1/customers/CUST-0001/relationship-accounts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value("CUST-0001"))
        .andExpect(jsonPath("$.accounts[0].accountNumber").value("LN100001"))
        .andExpect(jsonPath("$.page.totalElements").value(1));
  }

  @Test
  void getRelationshipAccounts_customerNotFound_returns404() throws Exception {
    when(relationshipAccountService.getRelationshipAccounts(
            any(), any(), anyBoolean(), anyInt(), anyInt()))
        .thenThrow(new CustomerNotFoundException("UNKNOWN"));

    mockMvc
        .perform(get("/api/v1/customers/UNKNOWN/relationship-accounts"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Customer Not Found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void getRelationshipAccounts_bigtableFailure_returns503() throws Exception {
    when(relationshipAccountService.getRelationshipAccounts(
            any(), any(), anyBoolean(), anyInt(), anyInt()))
        .thenThrow(new DatastoreException("Bigtable timeout"));

    mockMvc
        .perform(
            get("/api/v1/customers/CUST-0001/relationship-accounts")
                .param("includeLoanDetails", "true"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503));
  }

  @Test
  void getRelationshipAccounts_constraintViolation_returns400() throws Exception {
    when(relationshipAccountService.getRelationshipAccounts(
            any(), any(), anyBoolean(), anyInt(), eq(200)))
        .thenThrow(new ConstraintViolationException(Set.of()));

    mockMvc
        .perform(get("/api/v1/customers/CUST-0001/relationship-accounts").param("size", "200"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void getRelationshipAccounts_unexpectedError_returns500() throws Exception {
    when(relationshipAccountService.getRelationshipAccounts(
            any(), any(), anyBoolean(), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("unexpected"));

    mockMvc
        .perform(get("/api/v1/customers/CUST-0001/relationship-accounts"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500));
  }

  @Test
  void getRelationshipAccounts_invalidEnumValue_returns400MethodArgumentTypeMismatch()
      throws Exception {
    mockMvc
        .perform(
            get("/api/v1/customers/CUST-0001/relationship-accounts")
                .param("relationshipType", "INVALID_ENUM_VALUE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }
}
