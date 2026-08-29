# 5. Identifiers do not leave the process

*Accepted.*

## Context

The classifier sends a resident's message to a third party. Residents write things like
"hola, mi dni es 20.123.456 y quiero renovar el carnet", so a document number goes with it
— to a service outside the municipality's network, in a request that may be logged at the
other end, for a question that does not need it.

Every log line, metric and trace has the same problem in a smaller way: the resident is
known here by their phone number.

## Decision

Two rules, at two different boundaries.

**Leaving the process.** `Confidential.withoutIdentifiers` replaces every run of four or
more digits, and every claim number, before the message reaches the model. Deliberately
blunt: a phone number, a card number and a document number look the same from there, and
being wrong about which is which is the failure worth removing. Three digits and fewer stay
— those are house numbers and hours of the day, and a complaint with the address taken out
of it is harder to route, not safer.

**Being written down.** Nothing a resident wrote is logged: not the message, not what was
read out of it, not the id they are known by. Logs carry an HMAC pseudonym under a
configured secret, the *names* of what is known, and numbers. The API response follows the
same rule — the caller already has the document number they sent, and this response ends up
in their logs too.

## Consequences

The classifier is not weakened. No number a resident types makes a message more or less
about licences.

The agent still reads the resident's actual words: masking happens on the way out, not on
the way in, because the document number is needed to file anything.

Support gets a stable name for "the same person as the line above" without anybody's phone
number in a dashboard. With no secret configured, one is generated per run, which is safe
and makes yesterday's logs unfollowable — so it is a warning at startup.

What is still in the clear: the message text itself, minus the numbers, reaches the model.
A municipality that cannot accept that runs a model inside its own network. The classifier
is handed a `ChatClient` and does not know the difference.
