// Tests for LoanNotesService — verifies idempotency key generation and client delegation.
package com.example.lending.loandata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.CreateNoteRequest;
import com.example.lending.loandata.model.dto.CreateNoteResponse;
import com.example.lending.loandata.observability.CorrelationIdFilter;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class LoanNotesServiceTest {

  @Mock LoanServicingClient loanServicingClient;
  @InjectMocks LoanNotesService service;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void createNote_usesProvidedIdempotencyKey() {
    CreateNoteRequest req =
        new CreateNoteRequest(CreateNoteRequest.NoteType.GENERAL, "test note", "user1");
    CreateNoteResponse resp =
        new CreateNoteResponse(
            "note-1", "LN1000000001", "GENERAL", "test note", "user1", OffsetDateTime.now());
    when(loanServicingClient.createNote("LN1000000001", "", "key-123", req)).thenReturn(resp);

    CreateNoteResponse result = service.createNote("LN1000000001", "key-123", req);

    assertThat(result).isEqualTo(resp);
    verify(loanServicingClient).createNote("LN1000000001", "", "key-123", req);
  }

  @Test
  void createNote_generatesIdempotencyKeyWhenNotProvided() {
    CreateNoteRequest req =
        new CreateNoteRequest(CreateNoteRequest.NoteType.PAYMENT, "note", "user2");
    CreateNoteResponse resp =
        new CreateNoteResponse(
            "note-2", "LN1000000001", "PAYMENT", "note", "user2", OffsetDateTime.now());
    when(loanServicingClient.createNote(eq("LN1000000001"), eq(""), any(String.class), eq(req)))
        .thenReturn(resp);

    CreateNoteResponse result = service.createNote("LN1000000001", null, req);

    assertThat(result).isEqualTo(resp);
    verify(loanServicingClient)
        .createNote(eq("LN1000000001"), eq(""), argThat(k -> k != null && !k.isBlank()), eq(req));
  }

  @Test
  void createNote_propagatesCorrelationIdFromMdc() {
    MDC.put(CorrelationIdFilter.MDC_KEY, "corr-abc");
    CreateNoteRequest req =
        new CreateNoteRequest(CreateNoteRequest.NoteType.LEGAL, "legal note", "user3");
    CreateNoteResponse resp =
        new CreateNoteResponse(
            "note-3", "LN1000000001", "LEGAL", "legal note", "user3", OffsetDateTime.now());
    when(loanServicingClient.createNote("LN1000000001", "corr-abc", "idem-key", req))
        .thenReturn(resp);

    CreateNoteResponse result = service.createNote("LN1000000001", "idem-key", req);

    assertThat(result).isEqualTo(resp);
    verify(loanServicingClient).createNote("LN1000000001", "corr-abc", "idem-key", req);
  }
}
