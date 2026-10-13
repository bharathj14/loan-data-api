// Config layer: wires RestClient + HttpServiceProxyFactory for the downstream Loan Servicing API.
package com.example.lending.loandata.config;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.client.RetryClientHttpRequestInterceptor;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class DownstreamClientConfig {

  private final AppProperties props;

  public DownstreamClientConfig(AppProperties props) {
    this.props = props;
  }

  @Bean
  public LoanServicingClient loanServicingClient() {
    AppProperties.Downstream downstream = props.downstream();
    AppProperties.Downstream.Retry retryProps = downstream.retry();

    RetryTemplate retryTemplate = new RetryTemplate();

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(retryProps.maxAttempts());
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(retryProps.initialBackoffMs());
    backOff.setMultiplier(retryProps.backoffMultiplier());
    retryTemplate.setBackOffPolicy(backOff);

    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(downstream.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(downstream.readTimeoutMs()));

    RestClient restClient =
        RestClient.builder()
            .baseUrl(downstream.baseUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(new RetryClientHttpRequestInterceptor(retryTemplate))
            .build();

    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(LoanServicingClient.class);
  }
}
