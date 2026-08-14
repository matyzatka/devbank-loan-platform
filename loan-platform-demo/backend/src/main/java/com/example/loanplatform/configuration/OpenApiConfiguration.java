package com.example.loanplatform.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Declares public API metadata; endpoint-specific contracts remain colocated with controllers. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Loan Platform API",
        version = "v1",
        description = "Demonstration platform for processing corporate loan applications"))
public class OpenApiConfiguration {
}
