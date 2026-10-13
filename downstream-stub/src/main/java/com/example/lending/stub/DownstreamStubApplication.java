// TEMPORARY: in the target architecture this is a separate repository/service (2nd repo).
// It is kept here only so the POC runs standalone. Never deployed.
// Layer: application entry point — downstream-stub module.
package com.example.lending.stub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DownstreamStubApplication {
  public static void main(String[] args) {
    SpringApplication.run(DownstreamStubApplication.class, args);
  }
}
