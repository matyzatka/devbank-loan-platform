package com.example.loanplatform.application;

/** Decouples use cases from the HTTP/MDC mechanism that supplies causal request context. */
public interface RequestIdProvider {

    String currentOrGenerate();
}
