package dev.bank.loanplatform.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanProcessingWorkerContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSupportedEventVersion() throws Exception {
        var event = objectMapper.readTree("{\"eventVersion\":1}");

        assertThatCode(() -> LoanProcessingWorker.validateEventVersion(event)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedOrMissingEventVersion() throws Exception {
        var unsupported = objectMapper.readTree("{\"eventVersion\":2}");
        var missing = objectMapper.readTree("{}");

        assertThatThrownBy(() -> LoanProcessingWorker.validateEventVersion(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion: 2");
        assertThatThrownBy(() -> LoanProcessingWorker.validateEventVersion(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion: missing");
    }
}
