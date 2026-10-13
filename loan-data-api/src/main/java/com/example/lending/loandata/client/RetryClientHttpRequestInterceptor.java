// Client layer: Spring RestClient interceptor applying RetryTemplate to GET requests only.
package com.example.lending.loandata.client;

import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.RetryCallback;
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
      return retryTemplate.execute(
          (RetryCallback<ClientHttpResponse, IOException>)
              ctx -> {
                ClientHttpResponse response = execution.execute(request, body);
                int statusCode = response.getStatusCode().value();
                // Retry on 5xx or 429 — these are transient; connection errors also trigger retry.
                if (statusCode >= 500 || statusCode == 429) {
                  response.close();
                  throw new IOException("Retryable HTTP status: " + statusCode);
                }
                return response;
              });
    }
    return execution.execute(request, body);
  }
}
