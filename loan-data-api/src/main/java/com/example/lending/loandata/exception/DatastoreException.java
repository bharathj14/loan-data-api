package com.example.lending.loandata.exception;

/**
 * Wraps transient or infrastructure failures from Bigtable (gRPC) and AlloyDB (JDBC). Maps to HTTP
 * 503 Service Unavailable. Internal error details are never included in the HTTP response.
 */
public class DatastoreException extends RuntimeException {

  public DatastoreException(String message, Throwable cause) {
    super(message, cause);
  }

  public DatastoreException(String message) {
    super(message);
  }
}
