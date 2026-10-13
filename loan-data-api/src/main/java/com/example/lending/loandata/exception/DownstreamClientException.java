// Exception layer: wraps downstream HTTP errors with our status code mapping.
package com.example.lending.loandata.exception;

import org.springframework.http.HttpStatus;

public class DownstreamClientException extends RuntimeException {

  private final HttpStatus status;
  private final String downstreamPath;

  public DownstreamClientException(HttpStatus status, String downstreamPath, String message) {
    super(message);
    this.status = status;
    this.downstreamPath = downstreamPath;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getDownstreamPath() {
    return downstreamPath;
  }
}
