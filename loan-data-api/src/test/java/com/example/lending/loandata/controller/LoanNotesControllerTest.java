// Test layer: controller slice tests for LoanNotesController.
package com.example.lending.loandata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.loandata.exception.DownstreamClientException;
import com.example.lending.loandata.exception.GlobalExceptionHandler;
import com.example.lending.loandata.exception.LoanNotFoundException;
import com.example.lending.loandata.model.dto.CreateNoteRequest;
import com.example.lending.loandata.model.dto.CreateNoteResponse;
import com.example.lending.loandata.service.LoanNotesService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LoanNotesControllerTest {

  @Mock private LoanNotesService loanNotesService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new LoanNotesController(loanNotesService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createNote_validRequest_returns200WithBody() throws Exception {
    CreateNoteResponse response =
        new CreateNoteResponse(
            "note-abc", "LN1000000001", "GENERAL", "Test note text", "user1", OffsetDateTime.now());

    when(loanNotesService.createNote(
            eq("LN1000000001"), eq("idem-key-1"), any(CreateNoteRequest.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/loans/LN1000000001/notes")
                .header("Idempotency-Key", "idem-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"noteType\":\"GENERAL\",\"noteText\":\"Test note text\",\"createdBy\":\"user1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.noteId").value("note-abc"))
        .andExpect(jsonPath("$.loanNumber").value("LN1000000001"))
        .andExpect(jsonPath("$.noteType").value("GENERAL"));
  }

  @Test
  void createNote_missingNoteText_returns400ProblemDetail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/loans/LN1000000001/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noteType\":\"GENERAL\",\"createdBy\":\"user1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void createNote_loanNotFound_returns404ProblemDetail() throws Exception {
    when(loanNotesService.createNote(eq("LN9999999999"), any(), any(CreateNoteRequest.class)))
        .thenThrow(new LoanNotFoundException("LN9999999999"));

    mockMvc
        .perform(
            post("/api/v1/loans/LN9999999999/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"noteType\":\"GENERAL\",\"noteText\":\"Test\",\"createdBy\":\"user1\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Loan Not Found"))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void createNote_downstreamError_returns502ProblemDetail() throws Exception {
    when(loanNotesService.createNote(eq("LN1000000001"), any(), any(CreateNoteRequest.class)))
        .thenThrow(
            new DownstreamClientException(
                HttpStatus.BAD_GATEWAY, "/v1/loans/LN1000000001/notes", "upstream 502"));

    mockMvc
        .perform(
            post("/api/v1/loans/LN1000000001/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"noteType\":\"GENERAL\",\"noteText\":\"Test\",\"createdBy\":\"user1\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.detail").value("Downstream service error"));
  }
}
