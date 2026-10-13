// Model layer: DTO for the POST /api/v1/loans/{loanNumber}/notes request body.
package com.example.lending.loandata.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
    @NotNull NoteType noteType,
    @NotBlank @Size(max = 2000) String noteText,
    @NotBlank @Size(max = 100) String createdBy) {

  public enum NoteType {
    GENERAL,
    PAYMENT,
    LEGAL
  }
}
