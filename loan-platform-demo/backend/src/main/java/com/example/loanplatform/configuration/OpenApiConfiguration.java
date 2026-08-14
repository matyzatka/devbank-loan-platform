package com.example.loanplatform.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "Loan Platform API",
        version = "v1",
        description = "Corporate loan application processing demonstrator"))
public class OpenApiConfiguration {
}
