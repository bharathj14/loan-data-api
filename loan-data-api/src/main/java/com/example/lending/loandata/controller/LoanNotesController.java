// Controller layer: exposes POST /api/v1/loans/{loanNumber}/notes proxying to the downstream service.
package com.example.lending.loandata.controller;

import com.example.lending.loandata.model.dto.CreateNoteRequest;
import com.example.lending.loandata.model.dto.CreateNoteResponse;
import com.example.lending.loandata.service.LoanNotesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@Validated
@Tag(name = "Loan Notes", description = "Create notes on a loan via the downstream service")
public class LoanNotesController {

  private final LoanNotesService loanNotesService;

  public LoanNotesController(LoanNotesService loanNotesService) {
    this.loanNotesService = loanNotesService;
  }

  @PostMapping("/{loanNumber}/notes")
  @Operation(summary = "Create a note on a loan")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Note created",
        content = @Content(schema = @Schema(implementation = CreateNoteResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid input",
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
  public ResponseEntity<CreateNoteResponse> createNote(
      @Parameter(description = "Loan number (format: LN followed by 10 digits)")
          @PathVariable
          @Pattern(regexp = "LN\\d{10}")
          String loanNumber,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateNoteRequest request) {
    return ResponseEntity.ok(loanNotesService.createNote(loanNumber, idempotencyKey, request));
  }
}
