package com.example.lending.loandata.controller;

import com.example.lending.loandata.model.dto.RelationshipAccountsResponse;
import com.example.lending.loandata.model.entity.RelationshipType;
import com.example.lending.loandata.service.RelationshipAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes read-only relationship-account operations. All business logic (including the parallel
 * Bigtable enrichment) is delegated to {@link RelationshipAccountService}.
 */
@RestController
@RequestMapping("/api/v1/customers")
@Validated
@Tag(name = "Customers", description = "Read customer relationship accounts from AlloyDB")
public class RelationshipAccountController {

  private final RelationshipAccountService relationshipAccountService;

  public RelationshipAccountController(RelationshipAccountService relationshipAccountService) {
    this.relationshipAccountService = relationshipAccountService;
  }

  @GetMapping("/{customerId}/relationship-accounts")
  @Operation(
      summary = "List relationship accounts for a customer",
      description =
          "Returns paginated accounts linked to the customer in AlloyDB. "
              + "When includeLoanDetails=true, LOAN-type accounts on the current page "
              + "are enriched with a summary fetched from Cloud Bigtable in parallel. "
              + "If any Bigtable fetch fails the entire request returns 503.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Accounts found"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Customer not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Bigtable or AlloyDB unavailable",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public RelationshipAccountsResponse getRelationshipAccounts(
      @Parameter(description = "Customer ID", example = "CUST-0042") @PathVariable @NotBlank
          String customerId,
      @Parameter(description = "Filter by relationship type") @RequestParam(required = false)
          RelationshipType relationshipType,
      @Parameter(description = "Attach Bigtable loan summary to LOAN accounts", example = "false")
          @RequestParam(defaultValue = "false")
          boolean includeLoanDetails,
      @Parameter(description = "Zero-based page number", example = "0")
          @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Page size (1–100)", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {
    return relationshipAccountService.getRelationshipAccounts(
        customerId, relationshipType, includeLoanDetails, page, size);
  }
}
