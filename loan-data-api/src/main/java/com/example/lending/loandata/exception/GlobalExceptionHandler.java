package com.example.lending.loandata.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain exceptions to RFC 7807 {@link ProblemDetail} responses. Internal details (SQL,
 * row keys, stack traces, customer data) are never included in the response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(LoanNotFoundException.class)
  public ProblemDetail handleLoanNotFound(LoanNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setType(URI.create("urn:problem:loan-not-found"));
    problem.setTitle("Loan Not Found");
    problem.setDetail("The requested loan number does not exist.");
    return problem;
  }

  @ExceptionHandler(CustomerNotFoundException.class)
  public ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setType(URI.create("urn:problem:customer-not-found"));
    problem.setTitle("Customer Not Found");
    problem.setDetail("The requested customer ID does not exist.");
    return problem;
  }

  @ExceptionHandler(DatastoreException.class)
  public ProblemDetail handleDatastore(DatastoreException ex) {
    log.error("Datastore error: {}", ex.getMessage());
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
    problem.setType(URI.create("urn:problem:service-unavailable"));
    problem.setTitle("Service Unavailable");
    problem.setDetail("A downstream data service is temporarily unavailable.");
    return problem;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    String violations =
        ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setType(URI.create("urn:problem:validation-error"));
    problem.setTitle("Bad Request");
    problem.setDetail(violations);
    return problem;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setType(URI.create("urn:problem:validation-error"));
    problem.setTitle("Bad Request");
    problem.setDetail("Invalid value for parameter: " + ex.getName());
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setType(URI.create("urn:problem:internal-error"));
    problem.setTitle("Internal Server Error");
    problem.setDetail("An unexpected error occurred.");
    return problem;
  }
}
