package com.example.lending.loandata.controller;

import com.example.lending.loandata.model.dto.LoanDetailResponse;
import com.example.lending.loandata.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes read-only loan operations. All business logic is delegated to {@link LoanService}. */
@RestController
@RequestMapping("/api/v1/loans")
@Validated
@Tag(name = "Loans", description = "Read loan details from Cloud Bigtable")
public class LoanController {

  private final LoanService loanService;

  public LoanController(LoanService loanService) {
    this.loanService = loanService;
  }

  @GetMapping("/{loanNumber}")
  @Operation(
      summary = "Get loan details",
      description =
          "Returns full loan details for a single loan number read from Cloud Bigtable."
              + " The loan number must be 6–20 alphanumeric characters.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Loan found"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid loan number format",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Loan not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Bigtable unavailable",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public LoanDetailResponse getLoan(
      @Parameter(description = "Alphanumeric loan number, 6–20 characters", example = "LN100042")
          @PathVariable
          @Pattern(
              regexp = "^[A-Za-z0-9]{6,20}$",
              message = "loanNumber must be 6–20 alphanumeric characters")
          String loanNumber) {
    return loanService.getLoanDetail(loanNumber);
  }
}
