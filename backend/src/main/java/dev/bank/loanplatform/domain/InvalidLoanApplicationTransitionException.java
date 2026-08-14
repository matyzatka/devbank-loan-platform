package dev.bank.loanplatform.domain;

/** Domain failure carrying both states so API adapters can return a structured conflict. */
public final class InvalidLoanApplicationTransitionException extends RuntimeException {

    private final LoanApplicationStatus currentStatus;
    private final LoanApplicationStatus targetStatus;

    public InvalidLoanApplicationTransitionException(
            LoanApplicationStatus currentStatus,
            LoanApplicationStatus targetStatus) {
        super("Loan application cannot transition from %s to %s"
                .formatted(currentStatus, targetStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public LoanApplicationStatus currentStatus() {
        return currentStatus;
    }

    public LoanApplicationStatus targetStatus() {
        return targetStatus;
    }
}
