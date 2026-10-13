// Controller layer: exposes GET /api/v1/loans/{loanNumber}/payments proxying to the downstream
// service.
package com.example.lending.loandata.controller;

import com.example.lending.loandata.model.dto.PaymentHistoryResponse;
import com.example.lending.loandata.service.LoanPaymentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@Validated
@Tag(name = "Loan Payments", description = "Payment history for a loan from the downstream service")
public class LoanPaymentsController {

  private final LoanPaymentsService loanPaymentsService;

  public LoanPaymentsController(LoanPaymentsService loanPaymentsService) {
    this.loanPaymentsService = loanPaymentsService;
  }

  @GetMapping("/{loanNumber}/payments")
  @Operation(summary = "Get payment history for a loan")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Payment history returned",
        content = @Content(schema = @Schema(implementation = PaymentHistoryResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid loan number format",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Loan not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "502",
        description = "Downstream service error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Downstream service unavailable",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<PaymentHistoryResponse> getPayments(
      @Parameter(description = "Loan number (format: LN followed by 10 digits)")
          @PathVariable
          @Pattern(regexp = "LN\\d{10}")
          String loanNumber) {
    return ResponseEntity.ok(loanPaymentsService.getPayments(loanNumber));
  }
}
