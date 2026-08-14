package dev.bank.loanplatform.application;

/** Runtime actor responsible for an audited state transition. */
public enum StatusChangeSource {
    API,
    WORKER
}
