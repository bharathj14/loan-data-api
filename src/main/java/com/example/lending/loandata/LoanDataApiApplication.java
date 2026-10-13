package com.example.lending.loandata;

import com.example.lending.loandata.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class LoanDataApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(LoanDataApiApplication.class, args);
  }
}
