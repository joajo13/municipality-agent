package com.municipality.agent.observability;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What something cost, in money.
 *
 * <p>{@link BigDecimal} rather than a double, and six decimal places rather than two. One
 * turn of one conversation costs a fraction of a cent; rounded to cents it costs nothing
 * at all, and a million of them would still cost nothing. The rounding happens once, at
 * six places, and additions after that are exact.
 */
public record Cost(BigDecimal amount, String currency) {

    /** Enough places that a single turn is still a number rather than zero. */
    public static final int PLACES = 6;

    public Cost {
        if (amount == null) throw new IllegalArgumentException("amount is required");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        if (amount.signum() < 0) throw new IllegalArgumentException("amount must not be negative");

        amount = amount.setScale(PLACES, RoundingMode.HALF_UP);
    }

    public static Cost nothing(String currency) {
        return new Cost(BigDecimal.ZERO, currency);
    }

    public Cost plus(Cost other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("cannot add " + other.currency + " to " + currency);
        }

        return new Cost(amount.add(other.amount), currency);
    }

    public boolean isNothing() {
        return amount.signum() == 0;
    }

    /** For a metric, which counts in doubles and cannot be given anything else. */
    public double asDouble() {
        return amount.doubleValue();
    }
}
