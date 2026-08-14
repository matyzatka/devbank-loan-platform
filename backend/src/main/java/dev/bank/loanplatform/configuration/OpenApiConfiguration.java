package dev.bank.loanplatform.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Declares public API metadata; endpoint-specific contracts remain colocated with controllers. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
        title = "DevBank API",
        version = "v1",
        description = "API ukázkového prostředí pro zpracování žádostí o korporátní úvěry"))
public class OpenApiConfiguration {
}
