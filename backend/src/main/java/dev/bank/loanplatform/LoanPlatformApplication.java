package dev.bank.loanplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Shared executable artifact configured at runtime as either Loan API or processing worker. */
@SpringBootApplication
public class LoanPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanPlatformApplication.class, args);
    }
}
