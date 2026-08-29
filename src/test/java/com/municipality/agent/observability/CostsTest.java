package com.municipality.agent.observability;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Turning tokens into money.
 *
 * <p>The prices here are round numbers so that the arithmetic can be checked by reading
 * it: a dollar per million in, five per million out.
 */
class CostsTest {

    private static final PricingProperties PRICES = new PricingProperties("USD",
            Map.of("test-model", new PricingProperties.Price(new BigDecimal("1.00"), new BigDecimal("5.00"))));

    private final Costs costs = new Costs(PRICES);

    private static ModelCall call(String model, long in, long out) {
        return new ModelCall(model, in, out, Duration.ofMillis(300));
    }

    @Test
    void tokensBecomeMoney() {
        // A million in at a dollar, a million out at five.
        assertThat(costs.of(call("test-model", 1_000_000, 1_000_000)).amount()).isEqualByComparingTo("6.00");
    }

    @Test
    void aRealTurnCostsAFractionOfACent() {
        // 412 in and 18 out: 0.000412 + 0.00009.
        assertThat(costs.of(call("test-model", 412, 18)).amount()).isEqualByComparingTo("0.000502");
    }

    @Test
    void aCallThatSpentNothingCostsNothing() {
        assertThat(costs.of(call("test-model", 0, 0)).isNothing()).isTrue();
    }

    @Test
    void aModelNobodyPricedCostsNothingAndSaysSo() {
        // Counting it as nothing is a lie, and the only defensible thing to do about it
        // is to be loud: a warning, and a metric that can be alerted on. Failing the turn
        // would take the service down over a missing line of configuration.
        assertThat(costs.of(call("some-new-model", 1000, 100)).isNothing()).isTrue();
        assertThat(costs.knowsThePriceOf("some-new-model")).isFalse();
        assertThat(costs.knowsThePriceOf("test-model")).isTrue();
    }

    @Test
    void everythingIsPricedInTheOneCurrency() {
        assertThat(costs.currency()).isEqualTo("USD");
        assertThat(costs.of(call("test-model", 1, 1)).currency()).isEqualTo("USD");
    }

    @Test
    void thereIsNoPricingWithoutPrices() {
        assertThatThrownBy(() -> new Costs(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPriceListWithNoModelsIsValidAndPricesNothing() {
        var empty = new Costs(new PricingProperties("USD", null));

        assertThat(empty.of(call("test-model", 1000, 1000)).isNothing()).isTrue();
    }

    @Test
    void aNegativePriceIsNotAPrice() {
        assertThatThrownBy(() -> new PricingProperties.Price(new BigDecimal("-1"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingProperties.Price(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
