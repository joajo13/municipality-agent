package com.municipality.agent;

import com.municipality.agent.observability.Costs;
import com.municipality.agent.observability.ModelCall;
import com.municipality.agent.observability.Pseudonyms;
import com.municipality.agent.message.IncomingMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Every turn, watched: one span, one log line, and the counters behind the two questions
 * anybody asks about an agent like this — why did it say that, and what is it costing.
 *
 * <p>It wraps the agent rather than living inside it. The agent is plain Java with no
 * framework in it, and the day this becomes two services the thing that has to move is
 * this class, not the rules underneath it.
 *
 * <p>Nothing a resident wrote is recorded. Not the message, not what was extracted from
 * it, not the id they are known by — the log line carries a pseudonym, the names of the
 * types involved, and numbers. Everything here ends up in a system with a longer memory
 * and a wider audience than this one.
 */
@Service
public class Turns {

    private static final Logger log = LoggerFactory.getLogger(Turns.class);

    /** Fields every log line of a turn carries, so one turn can be pulled out of a day of them. */
    private static final String TRACE_ID = "traceId";
    private static final String RESIDENT = "resident";

    private static final String TURN = "agent.turn";

    private final Agent agent;
    private final Costs costs;
    private final Pseudonyms pseudonyms;
    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    public Turns(
            Agent agent,
            Costs costs,
            Pseudonyms pseudonyms,
            ObservationRegistry observations,
            MeterRegistry meters) {

        this.agent = agent;
        this.costs = costs;
        this.pseudonyms = pseudonyms;
        this.observations = observations;
        this.meters = meters;
    }

    public Outcome handle(IncomingMessage message) {
        Observation observation = Observation.createNotStarted(TURN, observations)
                .lowCardinalityKeyValue("channel", "message")
                .start();

        MDC.put(TRACE_ID, message.traceId());
        MDC.put(RESIDENT, pseudonyms.of(message.userId()));

        try (Observation.Scope scope = observation.openScope()) {
            Outcome outcome = agent.handle(message);

            describe(observation, outcome);
            count(outcome);
            report(outcome);

            return outcome;
        } catch (RuntimeException failure) {
            observation.error(failure);
            throw failure;
        } finally {
            observation.stop();
            MDC.remove(TRACE_ID);
            MDC.remove(RESIDENT);
        }
    }

    /** Tags on the span. All four are small, closed sets — none of them is a resident's words. */
    private static void describe(Observation observation, Outcome outcome) {
        observation.lowCardinalityKeyValue("domain", outcome.intent().domain().name())
                .lowCardinalityKeyValue("action", outcome.intent().action().name())
                .lowCardinalityKeyValue("decision", outcome.decision().getClass().getSimpleName())
                .lowCardinalityKeyValue("model", modelOf(outcome));
    }

    private void count(Outcome outcome) {
        Tags what = Tags.of(
                "domain", outcome.intent().domain().name(),
                "action", outcome.intent().action().name(),
                "decision", outcome.decision().getClass().getSimpleName());

        meters.counter("agent.turns", what).increment();

        ModelCall call = outcome.trace().call();

        if (call == null) return;

        Tags model = Tags.of("model", call.model());

        meters.counter("agent.model.calls", model.and("priced", String.valueOf(costs.knowsThePriceOf(call.model()))))
                .increment();
        meters.counter("agent.model.tokens", model.and("direction", "input")).increment(call.inputTokens());
        meters.counter("agent.model.tokens", model.and("direction", "output")).increment(call.outputTokens());
        meters.counter("agent.model.cost", model.and("currency", outcome.trace().cost().currency()))
                .increment(outcome.trace().cost().asDouble());
    }

    /** One line per turn, which is what somebody reads at three in the morning. */
    private static void report(Outcome outcome) {
        var trace = outcome.trace();

        log.info("turn={} intent={}/{} confidence={} decision={} given={} took={}ms cost={} {}",
                outcome.conversation().turns(),
                outcome.intent().domain(),
                outcome.intent().action(),
                outcome.intent().confidence(),
                outcome.decision().getClass().getSimpleName(),
                outcome.given().keySet(),
                trace.took().toMillis(),
                trace.cost().amount().toPlainString(),
                trace.cost().currency());
    }

    private static String modelOf(Outcome outcome) {
        ModelCall call = outcome.trace().call();

        return call == null ? "none" : call.model();
    }
}
