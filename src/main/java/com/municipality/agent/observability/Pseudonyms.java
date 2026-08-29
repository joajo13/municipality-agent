package com.municipality.agent.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Turns a resident into a name that can be written down.
 *
 * <p>The id this agent knows a resident by is their phone number. Putting that in a log
 * line puts it in every system the logs are shipped to, kept for as long as those keep
 * anything, and readable by everybody who can read a dashboard. What is needed for
 * support is far less: a stable name for "the same person as the line above".
 *
 * <p>A plain hash would not do it. Phone numbers are a small enough space to enumerate,
 * so an unsalted digest is the number itself with extra steps. This is an HMAC under a
 * secret, and the secret decides what the pseudonyms mean:
 *
 * <ul>
 *   <li>Configured — the same resident is the same name across restarts and across
 *       instances, which is what makes a week-old incident followable.
 *   <li>Not configured — one is generated at startup, so the names hold within a run and
 *       nowhere else. Safe, and useless for anything but the run it came from, which is
 *       why it is a warning.
 * </ul>
 */
public class Pseudonyms {

    private static final Logger log = LoggerFactory.getLogger(Pseudonyms.class);

    private static final String ALGORITHM = "HmacSHA256";

    /** Long enough not to collide across a municipality, short enough to read in a log line. */
    private static final int LENGTH = 12;

    private final SecretKeySpec secret;

    public Pseudonyms(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("No pseudonym secret configured; residents get a new name every restart. "
                    + "Set agent.pseudonym-secret to follow one across restarts and instances.");

            secret = generated();
        }

        this.secret = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** A stable, meaningless name for this resident. */
    public String of(String userId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);

            return HexFormat.of().formatHex(mac.doFinal(userId.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, LENGTH);
        } catch (GeneralSecurityException impossible) {
            // HmacSHA256 is required of every JVM, and the key is a byte array we just made.
            throw new IllegalStateException("HMAC is unavailable", impossible);
        }
    }

    private static String generated() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
    }
}
