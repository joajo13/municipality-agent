package com.municipality.agent.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The one secret in front of the endpoint.
 *
 * <p>There is no mode where the endpoint is open. When nothing is configured a key is
 * generated at startup and printed once, which is the same bargain a database prints a
 * generated password under: a developer gets a working service in one command, and
 * nobody gets an unauthenticated one by forgetting a line of configuration.
 *
 * <p>The comparison is constant time. A comparison that returns early tells an attacker
 * how much of the key was right, and a few thousand requests turn that into the key.
 */
public class ApiKeys {

    private static final Logger log = LoggerFactory.getLogger(ApiKeys.class);

    private final byte[] expected;

    public ApiKeys(String configured) {
        if (configured == null || configured.isBlank()) {
            configured = generated();

            log.warn("No API key configured. Using a generated one for this run: {}", configured);
            log.warn("Set agent.api.key (or API_KEY) to a secret of your own before this goes anywhere.");
        }

        this.expected = configured.getBytes(StandardCharsets.UTF_8);
    }

    public boolean accepts(String offered) {
        if (offered == null) return false;

        return MessageDigest.isEqual(expected, offered.getBytes(StandardCharsets.UTF_8));
    }

    private static String generated() {
        var bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
