# 4. Golden transcripts as the specification

*Accepted.*

## Context

Unit tests answer one question about one part. The question a resident asks is different:
if I say this, and then this, and then this, what happens? Nothing in a suite of unit tests
answers that, and the parts of this system most likely to be wrong — what carries over
between turns, when a bare number is an answer, what a "gracias" does to a procedure — only
exist between turns.

Writing that as assertions produces tests that are long, hard to read, and quietly
loosened: `assertThat(decision).isInstanceOf(AskFor.class)` still passes when the reply
became nonsense.

## Decision

Conversations are files. The `you` lines are the script; everything under them is what the
agent wrote on the last run — the intent, the decision, what was handed over, what is
remembered, what it cost, and the reply. The test replays the script and compares the whole
file.

`-Dgolden.update=true` rewrites them. The diff is the review.

## Consequences

A change in behaviour arrives as a diff of a conversation, which is readable by somebody
who does not know the codebase. Writing them down immediately turned up a wart nobody had
noticed: every piece of smalltalk, including "gracias, chau", was answered with "¡Hola!".

Nothing in a transcript may vary between runs, so message ids come off a counter and
timings are not written down. A golden file that changes when nothing changed is a golden
file nobody reads.

The risk is accepting a bad diff without looking. CI regenerates the transcripts on every
change and fails if they moved, so the diff is always seen — but it is seen by whoever ran
`make golden`, and their attention is the only thing enforcing it.

A meta-test walks the enums and fails if any topic, action, outcome or identifier appears
in no conversation, so adding a domain with nobody talking to it is a failing test rather
than an oversight.
