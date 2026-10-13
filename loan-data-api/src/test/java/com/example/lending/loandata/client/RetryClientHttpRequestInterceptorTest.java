// Test layer: unit tests for RetryClientHttpRequestInterceptor retry behaviour.
package com.example.lending.loandata.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class RetryClientHttpRequestInterceptorTest {

  @Mock private org.springframework.http.HttpRequest request;
  @Mock private ClientHttpRequestExecution execution;

  private static final byte[] EMPTY_BODY = new byte[0];

  private RetryTemplate buildRetryTemplate(int maxAttempts) {
    RetryTemplate template = new RetryTemplate();
    template.setRetryPolicy(new SimpleRetryPolicy(maxAttempts));
    template.setBackOffPolicy(new NoBackOffPolicy());
    return template;
  }

  @Test
  void get_200_passesThroughWithoutRetry() throws IOException {
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(execution.execute(request, EMPTY_BODY)).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    RetryClientHttpRequestInterceptor interceptor =
        new RetryClientHttpRequestInterceptor(buildRetryTemplate(2));
    ClientHttpResponse result = interceptor.intercept(request, EMPTY_BODY, execution);

    assertThat(result).isEqualTo(response);
    verify(execution, times(1)).execute(request, EMPTY_BODY);
  }

  @Test
  void get_500_retriesUpToMaxAttemptsAndThrows() throws IOException {
    ClientHttpResponse errorResponse = mock(ClientHttpResponse.class);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(execution.execute(request, EMPTY_BODY)).thenReturn(errorResponse);
    when(errorResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(500));

    RetryClientHttpRequestInterceptor interceptor =
        new RetryClientHttpRequestInterceptor(buildRetryTemplate(2));

    assertThatThrownBy(() -> interceptor.intercept(request, EMPTY_BODY, execution))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("500");

    // maxAttempts=2: 1 initial attempt + 1 retry
    verify(execution, times(2)).execute(request, EMPTY_BODY);
  }

  @Test
  void get_429_retriesUpToMaxAttemptsAndThrows() throws IOException {
    ClientHttpResponse tooManyResponse = mock(ClientHttpResponse.class);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(execution.execute(request, EMPTY_BODY)).thenReturn(tooManyResponse);
    when(tooManyResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(429));

    RetryClientHttpRequestInterceptor interceptor =
        new RetryClientHttpRequestInterceptor(buildRetryTemplate(2));

    assertThatThrownBy(() -> interceptor.intercept(request, EMPTY_BODY, execution))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("429");

    verify(execution, times(2)).execute(request, EMPTY_BODY);
  }

  @Test
  void post_500_passesThroughImmediatelyWithoutRetry() throws IOException {
    ClientHttpResponse errorResponse = mock(ClientHttpResponse.class);
    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(execution.execute(request, EMPTY_BODY)).thenReturn(errorResponse);

    RetryClientHttpRequestInterceptor interceptor =
        new RetryClientHttpRequestInterceptor(buildRetryTemplate(3));
    ClientHttpResponse result = interceptor.intercept(request, EMPTY_BODY, execution);

    assertThat(result).isEqualTo(errorResponse);
    // POST must never be retried regardless of status
    verify(execution, times(1)).execute(request, EMPTY_BODY);
  }
}
