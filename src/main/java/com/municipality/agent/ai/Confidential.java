package com.municipality.agent.ai;

import java.util.regex.Pattern;

/**
 * Takes the identifiers out of a message before it leaves this process.
 *
 * <p>This is the only place in the system where what a resident wrote is sent to somebody
 * else's computer. What is being asked for is a topic and an action, and no number a
 * resident types helps with either — a document number does not make a message more or
 * less about licences. So the numbers do not go.
 *
 * <p>It runs on the way out rather than on the way in, deliberately. The agent still reads
 * the resident's actual words: it needs the document number to file anything, and the
 * console still shows the message as it arrived. What changes is only what crosses the
 * boundary, which is where the risk was.
 *
 * <p>It is deliberately blunt. Every long run of digits goes, not only the ones that
 * turned out to be identifiers — a phone number, a card number and a document number look
 * the same from here, and being wrong about which is which is the failure worth avoiding.
 */
final class Confidential {

    /** A claim number, which says what it is and is therefore worth naming as one. */
    private static final Pattern CLAIM = Pattern.compile("\\b[Rr][Ee][Cc]-\\d{4}-\\d{1,6}\\b");

    /**
     * Four or more digits, however they were grouped. Three or fewer stays: those are
     * house numbers and hours of the day, and a message with the street number taken out
     * of it is harder to route, not safer.
     */
    private static final Pattern NUMBER = Pattern.compile("\\b\\d{1,3}(?:[.\\s]\\d{3})+\\b|\\b\\d{4,}\\b");

    private Confidential() {}

    static String withoutIdentifiers(String text) {
        return NUMBER.matcher(CLAIM.matcher(text).replaceAll("[reclamo]")).replaceAll("[número]");
    }
}
