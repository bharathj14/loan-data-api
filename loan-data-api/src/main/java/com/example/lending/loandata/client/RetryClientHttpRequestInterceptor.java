// Client layer: Spring RestClient interceptor applying RetryTemplate to GET requests only.
package com.example.lending.loandata.client;

import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;

public class RetryClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private final RetryTemplate retryTemplate;

  public RetryClientHttpRequestInterceptor(RetryTemplate retryTemplate) {
    this.retryTemplate = retryTemplate;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    // Only GET requests are retried; POST must never be auto-retried (idempotency).
    if (request.getMethod() == HttpMethod.GET) {
      return retryTemplate.execute(ctx -> execution.execute(request, body));
    }
    return execution.execute(request, body);
  }
}
