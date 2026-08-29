package com.municipality.agent.observability;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What one turn took and what it cost. */
class TraceTest {

    private static final Cost FREE = Cost.nothing("USD");

    @Test
    void aTurnAnsweredFromAWordListReachedNoModel() {
        assertThat(new Trace("trace-1", Duration.ofMillis(3), null, FREE).reachedAModel()).isFalse();
    }

    @Test
    void aTurnThatAskedAModelSaysSo() {
        var call = new ModelCall("test-model", 412, 18, Duration.ofMillis(300));
        var trace = new Trace("trace-1", Duration.ofMillis(310), call, new Cost(new BigDecimal("0.0005"), "USD"));

        assertThat(trace.reachedAModel()).isTrue();
        assertThat(trace.call()).isEqualTo(call);
    }

    @Test
    void aTraceWithoutAnIdTiesToNothing() {
        assertThatThrownBy(() -> new Trace(" ", Duration.ZERO, null, FREE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTurnCannotTakeLessThanNoTime() {
        assertThatThrownBy(() -> new Trace("trace-1", Duration.ofMillis(-1), null, FREE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Trace("trace-1", null, null, FREE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTurnAlwaysCostSomethingEvenIfThatIsZero() {
        assertThatThrownBy(() -> new Trace("trace-1", Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
