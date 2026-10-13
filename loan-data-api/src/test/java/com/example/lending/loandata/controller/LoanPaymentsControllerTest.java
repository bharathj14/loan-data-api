// Test layer: controller slice tests for LoanPaymentsController.
package com.example.lending.loandata.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.loandata.exception.DownstreamClientException;
import com.example.lending.loandata.exception.GlobalExceptionHandler;
import com.example.lending.loandata.exception.LoanNotFoundException;
import com.example.lending.loandata.model.dto.PaymentHistoryResponse;
import com.example.lending.loandata.service.LoanPaymentsService;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LoanPaymentsControllerTest {

  @Mock private LoanPaymentsService loanPaymentsService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new LoanPaymentsController(loanPaymentsService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getPayments_validLoanNumber_returns200WithBody() throws Exception {
    PaymentHistoryResponse response =
        new PaymentHistoryResponse(
            "LN1000000001",
            2,
            LocalDate.of(2025, 2, 1),
            new BigDecimal("250.00"),
            List.of(
                new PaymentHistoryResponse.Payment(
                    1,
                    LocalDate.of(2025, 1, 1),
                    new BigDecimal("250.00"),
                    "PAID",
                    new BigDecimal("200.00"),
                    new BigDecimal("50.00"),
                    new BigDecimal("8300.00"))));

    when(loanPaymentsService.getPayments("LN1000000001")).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/loans/LN1000000001/payments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loanNumber").value("LN1000000001"))
        .andExpect(jsonPath("$.totalPayments").value(2))
        .andExpect(jsonPath("$.payments[0].status").value("PAID"));
  }

  @Test
  void getPayments_invalidLoanNumber_returns400ProblemDetail() throws Exception {
    when(loanPaymentsService.getPayments("BAD"))
        .thenThrow(new ConstraintViolationException(Set.of()));

    mockMvc
        .perform(get("/api/v1/loans/BAD/payments"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void getPayments_loanNotFound_returns404ProblemDetail() throws Exception {
    when(loanPaymentsService.getPayments("LN9999999999"))
        .thenThrow(new LoanNotFoundException("LN9999999999"));

    mockMvc
        .perform(get("/api/v1/loans/LN9999999999/payments"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Loan Not Found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void getPayments_downstreamError_returns502ProblemDetail() throws Exception {
    when(loanPaymentsService.getPayments("LN1000000001"))
        .thenThrow(
            new DownstreamClientException(
                HttpStatus.BAD_GATEWAY, "/v1/loans/LN1000000001/payments", "upstream 502"));

    mockMvc
        .perform(get("/api/v1/loans/LN1000000001/payments"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.detail").value("Downstream service error"));
  }
}
