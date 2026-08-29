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
 * <p>There is no mode where the endpoint is open, and no mode where forgetting to set
 * this is survivable. A service with no key configured does not start.
 *
 * <p>The one exception has to be asked for. {@code agent.api.allow-generated-key} makes a
 * key up and prints it, which is what the console profile and the compose file turn on so
 * that a developer gets a working service in one command. It is off by default and it has
 * to be written down somewhere, because the failure it would otherwise cause is silent:
 * a production service that generates a key, prints it into the log pipeline, and is then
 * reachable by everybody who can read a dashboard.
 *
 * <p>The comparison is constant time. A comparison that returns early tells an attacker
 * how much of the key was right, and a few thousand requests turn that into the key.
 */
public class ApiKeys {

    private static final Logger log = LoggerFactory.getLogger(ApiKeys.class);

    private final byte[] expected;

    public ApiKeys(String configured, boolean mayGenerateOne) {
        if (configured == null || configured.isBlank()) {
            if (!mayGenerateOne) {
                throw new IllegalStateException(
                        "No API key is configured. Set agent.api.key (or the API_KEY environment variable). "
                                + "For a local run, agent.api.allow-generated-key=true makes one up and prints it.");
            }

            configured = generated();

            log.warn("No API key configured. Using a generated one for this run: {}", configured);
            log.warn("This is a development mode: the key above is now in whatever reads this log.");
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
