// Test layer: unit tests for GlobalExceptionHandler RFC 7807 ProblemDetail mapping.
package com.example.lending.loandata.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleLoanNotFound_returns404WithTitle() {
    ProblemDetail result = handler.handleLoanNotFound(new LoanNotFoundException("LN1000000001"));
    assertThat(result.getStatus()).isEqualTo(404);
    assertThat(result.getTitle()).isEqualTo("Loan Not Found");
    assertThat(result.getDetail()).isEqualTo("The requested loan number does not exist.");
  }

  @Test
  void handleCustomerNotFound_returns404WithTitle() {
    ProblemDetail result =
        handler.handleCustomerNotFound(new CustomerNotFoundException("CUST-0001"));
    assertThat(result.getStatus()).isEqualTo(404);
    assertThat(result.getTitle()).isEqualTo("Customer Not Found");
  }

  @Test
  void handleDownstreamClientException_badGateway_returns502() {
    DownstreamClientException ex =
        new DownstreamClientException(
            HttpStatus.BAD_GATEWAY, "/v1/loans/LN1000000001/payments", "upstream error");
    ProblemDetail result = handler.handleDownstreamClientException(ex);
    assertThat(result.getStatus()).isEqualTo(502);
    assertThat(result.getDetail()).isEqualTo("Downstream service error");
  }

  @Test
  void handleDownstreamClientException_serviceUnavailable_returns503() {
    DownstreamClientException ex =
        new DownstreamClientException(
            HttpStatus.SERVICE_UNAVAILABLE, "/v1/loans/LN1000000001/notes", "timeout");
    ProblemDetail result = handler.handleDownstreamClientException(ex);
    assertThat(result.getStatus()).isEqualTo(503);
  }

  @Test
  void handleDatastore_returns503WithTitle() {
    ProblemDetail result = handler.handleDatastore(new DatastoreException("Bigtable timeout"));
    assertThat(result.getStatus()).isEqualTo(503);
    assertThat(result.getTitle()).isEqualTo("Service Unavailable");
  }

  @Test
  void handleConstraintViolation_returns400() {
    ProblemDetail result =
        handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getTitle()).isEqualTo("Bad Request");
  }

  @Test
  void handleMethodArgumentNotValid_returns400WithFieldViolation() {
    MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "createNoteRequest");
    bindingResult.rejectValue("noteText", "NotBlank", "must not be blank");
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
    ProblemDetail result = handler.handleMethodArgumentNotValid(ex);
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getDetail()).contains("noteText");
  }

  @Test
  void handleTypeMismatch_returns400WithParamName() {
    MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
    when(ex.getName()).thenReturn("loanNumber");
    ProblemDetail result = handler.handleTypeMismatch(ex);
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getDetail()).contains("loanNumber");
  }

  @Test
  void handleResourceAccessException_returns502() {
    ProblemDetail result =
        handler.handleResourceAccessException(new ResourceAccessException("Connection refused"));
    assertThat(result.getStatus()).isEqualTo(502);
    assertThat(result.getDetail()).isEqualTo("Downstream service unavailable");
  }

  @Test
  void handleUnexpected_returns500() {
    ProblemDetail result = handler.handleUnexpected(new RuntimeException("Something went wrong"));
    assertThat(result.getStatus()).isEqualTo(500);
    assertThat(result.getTitle()).isEqualTo("Internal Server Error");
  }
}
