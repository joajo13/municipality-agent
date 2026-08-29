package com.municipality.agent.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a call to a model cost, from the tokens it reported and the prices it was
 * configured with.
 *
 * <p>A model nobody priced costs nothing, which is a lie, and the only defensible thing
 * to do about it is to say so out loud: once per model, at warning level, plus a metric
 * that can be alerted on. Failing the turn instead would take the service down over a
 * missing line of configuration; charging a made-up number would be worse than both.
 */
public class Costs {

    private static final Logger log = LoggerFactory.getLogger(Costs.class);

    private static final BigDecimal PER_MILLION = new BigDecimal("1000000");

    private final PricingProperties pricing;

    /** Models already complained about, so that the warning is a warning and not a stream. */
    private final Set<String> unpriced = ConcurrentHashMap.newKeySet();

    public Costs(PricingProperties pricing) {
        if (pricing == null) throw new IllegalArgumentException("pricing is required");

        this.pricing = pricing;
    }

    public String currency() {
        return pricing.currency();
    }

    public Cost of(ModelCall call) {
        PricingProperties.Price price = pricing.models().get(call.model());

        if (price == null) {
            if (unpriced.add(call.model())) {
                log.warn("No price configured for model {}; its turns will be counted as costing nothing.",
                        call.model());
            }
            return Cost.nothing(currency());
        }

        BigDecimal amount = perToken(price.inputPerMillion()).multiply(BigDecimal.valueOf(call.inputTokens()))
                .add(perToken(price.outputPerMillion()).multiply(BigDecimal.valueOf(call.outputTokens())));

        return new Cost(amount, currency());
    }

    /** Whether this model has a price at all, for a metric that says so without waiting for a bill. */
    public boolean knowsThePriceOf(String model) {
        return pricing.models().containsKey(model);
    }

    private static BigDecimal perToken(BigDecimal perMillion) {
        return perMillion.divide(PER_MILLION, MathContext.DECIMAL64);
    }
}
