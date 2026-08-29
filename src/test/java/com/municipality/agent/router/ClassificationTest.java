package com.municipality.agent.router;

import com.municipality.agent.observability.ModelCall;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a classifier concluded, and what it took to conclude it. */
class ClassificationTest {

    private static final Intent SOMETHING = new Intent(Domain.SALUD, Action.START_PROCEDURE, 1.0);

    @Test
    void aConclusionNobodyWasBilledForCarriesNoCall() {
        assertThat(Classification.free(SOMETHING).call()).isNull();
        assertThat(Classification.free(SOMETHING).intent()).isEqualTo(SOMETHING);
    }

    @Test
    void aConclusionSomebodyWasBilledForCarriesTheCall() {
        var call = new ModelCall("test-model", 10, 2, Duration.ofMillis(5));

        assertThat(new Classification(SOMETHING, call).call()).isEqualTo(call);
    }

    @Test
    void thereIsNoClassificationWithoutAnIntent() {
        assertThatThrownBy(() -> Classification.free(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
