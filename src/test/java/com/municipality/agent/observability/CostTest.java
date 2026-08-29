package com.municipality.agent.observability;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Money, and the two ways of getting it wrong: losing it to rounding, and adding up
 * things that are not the same currency.
 */
class CostTest {

    @Test
    void aFractionOfACentIsStillAFractionOfACent() {
        // Rounded to cents, one turn costs nothing, and so does a million of them.
        var cost = new Cost(new BigDecimal("0.0004325"), "USD");

        assertThat(cost.amount()).isEqualByComparingTo("0.000433");
        assertThat(cost.isNothing()).isFalse();
    }

    @Test
    void nothingIsNothing() {
        assertThat(Cost.nothing("USD").isNothing()).isTrue();
        assertThat(Cost.nothing("USD").asDouble()).isZero();
    }

    @Test
    void costsAddUp() {
        var total = new Cost(new BigDecimal("0.000100"), "USD").plus(new Cost(new BigDecimal("0.000250"), "USD"));

        assertThat(total.amount()).isEqualByComparingTo("0.000350");
    }

    @Test
    void twoCurrenciesDoNotAddUpToAnything() {
        var dollars = new Cost(BigDecimal.ONE, "USD");
        var pesos = new Cost(BigDecimal.ONE, "ARS");

        assertThatThrownBy(() -> dollars.plus(pesos)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeCostIsNotACost() {
        assertThatThrownBy(() -> new Cost(new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moneyWithoutACurrencyIsJustANumber() {
        assertThatThrownBy(() -> new Cost(BigDecimal.ONE, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cost(null, "USD")).isInstanceOf(IllegalArgumentException.class);
    }
}
