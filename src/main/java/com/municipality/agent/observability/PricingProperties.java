package com.municipality.agent.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What each model costs to use, per million tokens.
 *
 * <p>Configuration rather than constants, and deliberately so: prices change without
 * asking anybody here, and a price that lives in a jar is a price that is wrong until the
 * next release. The defaults are a starting point for the model this agent ships pointed
 * at, and they are worth checking against the provider's price list before anybody quotes
 * a number from them.
 *
 * @param currency what the prices below are in. One currency for the whole file: a
 *                 running total of two currencies is not a total.
 * @param models   keyed by the model id the provider reports, not the one that was asked
 *                 for — those differ the moment a provider aliases a name to a version.
 */
@ConfigurationProperties("agent.pricing")
public record PricingProperties(@DefaultValue("USD") String currency, Map<String, Price> models) {

    public PricingProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
    }

    /**
     * @param inputPerMillion  what a million tokens sent costs
     * @param outputPerMillion what a million tokens produced costs, which is never the same number
     */
    public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {

        public Price {
            if (inputPerMillion == null || outputPerMillion == null) {
                throw new IllegalArgumentException("both prices are required");
            }
            if (inputPerMillion.signum() < 0 || outputPerMillion.signum() < 0) {
                throw new IllegalArgumentException("prices must not be negative");
            }
        }
    }
}
