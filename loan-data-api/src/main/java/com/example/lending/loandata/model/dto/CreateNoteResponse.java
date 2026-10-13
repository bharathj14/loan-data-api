// Model layer: DTO for the downstream POST /v1/loans/{loanNumber}/notes response.
package com.example.lending.loandata.model.dto;

import java.time.OffsetDateTime;

public record CreateNoteResponse(
    String noteId,
    String loanNumber,
    String noteType,
    String noteText,
    String createdBy,
    OffsetDateTime createdAt) {}
