package com.example.lending.loandata.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.loandata.exception.DatastoreException;
import com.example.lending.loandata.exception.GlobalExceptionHandler;
import com.example.lending.loandata.exception.LoanNotFoundException;
import com.example.lending.loandata.model.dto.LoanDetailResponse;
import com.example.lending.loandata.service.LoanService;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LoanControllerTest {

  @Mock private LoanService loanService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new LoanController(loanService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getLoan_found_returns200WithBody() throws Exception {
    LoanDetailResponse response =
        new LoanDetailResponse(
            "LN100001",
            "PERSONAL_LOAN",
            "ACTIVE",
            new BigDecimal("10000.00"),
            new BigDecimal("8500.00"),
            new BigDecimal("5.25"),
            "USD",
            LocalDate.of(2022, 1, 15),
            LocalDate.of(2027, 1, 15),
            LocalDate.of(2024, 2, 1),
            new BigDecimal("250.00"),
            "CUST-0001");

    when(loanService.getLoanDetail("LN100001")).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/loans/LN100001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loanNumber").value("LN100001"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  @Test
  void getLoan_notFound_returns404ProblemDetail() throws Exception {
    when(loanService.getLoanDetail("LN999999")).thenThrow(new LoanNotFoundException("LN999999"));

    mockMvc
        .perform(get("/api/v1/loans/LN999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Loan Not Found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void getLoan_constraintViolation_returns400ProblemDetail() throws Exception {
    when(loanService.getLoanDetail("LN1")).thenThrow(new ConstraintViolationException(Set.of()));

    mockMvc
        .perform(get("/api/v1/loans/LN1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void getLoan_bigtableUnavailable_returns503ProblemDetail() throws Exception {
    when(loanService.getLoanDetail("LN100001"))
        .thenThrow(new DatastoreException("Bigtable timeout"));

    mockMvc
        .perform(get("/api/v1/loans/LN100001"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.title").value("Service Unavailable"))
        .andExpect(jsonPath("$.status").value(503));
  }

  @Test
  void getLoan_unexpectedError_returns500ProblemDetail() throws Exception {
    when(loanService.getLoanDetail("LN100001")).thenThrow(new RuntimeException("unexpected"));

    mockMvc
        .perform(get("/api/v1/loans/LN100001"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500));
  }
}
