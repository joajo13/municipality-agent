# How this is tested

*This document is the source. `docs/es/testing.md` is a translation of it — see
[translations](translations.md).*

---

```bash
make test       # the suite
make verify     # the suite, the coverage gate, the module boundaries
make golden     # rewrite the conversation transcripts, then read the diff
```

363 tests. 98.9% of lines and 91.8% of branches, both enforced: the build fails below 98%
and 90%. Lines and branches are counted separately because a suite that walks every line
down its happy path has not asked what happens when the answer is no.

## Four kinds of test, and what each is for

**Unit tests, no container.** Most of the suite. The message, routing, policy, conversation
and extraction packages have no framework in them, so their tests are plain Java: construct
the thing, call it, assert. Starting an application context is reserved for tests whose
subject *is* the context.

**Contract tests.** `ConversationsContract` and `ReceiptsContract` are the promises every
implementation has to keep, written once and run against both — the map and the table. A
stand-in that cannot fail the way production fails is a stand-in that hides the bug, so the
in-memory stores enforce the same conditional write as the database.

**Integration tests.** The persistence tests run against the real schema built by the real
migrations. The endpoint tests start the whole thing — real agent, real database, real
filter chain — because what they are testing is what the adapter adds: the key on the door,
the shape of a bad request, the second delivery of the same message.

**Golden transcripts.** Whole conversations, in `src/test/resources/golden/`. See
[ADR 4](adr/0004-golden-transcripts.md); the short version is that the file is both the
script and the expectation, nobody types the expected output, and a change in behaviour
arrives as a diff of a conversation.

## The transcripts

```
you       quiero consultar el estado de mi reclamo
  intent    RECLAMOS / CHECK_STATUS (1.00)
  decision  AskFor [CLAIM_NUMBER]
  given     -
  known     -
  awaiting  CLAIM_NUMBER
  turn      1
  bot       Para seguir necesito el número de reclamo.
```

The `you` lines are the input. Everything under them was written by the agent on the last
run. To change one, change the `you` lines, run `make golden`, and read the diff.

A header picks what is answering:

```
# classifier: keywords      the word list, free, sure or not at all
# classifier: billing       the same answers, billed like a model, so the cost line appears
# classifier: unreachable   a model that cannot be reached; every turn ends in the menu
```

A `you` line is text unless it starts with a prefix, which is how a transcript covers the
things a resident does that are not typing:

```
you       image:https://cdn.example/1.jpg|se rompio una luminaria
you       audio:https://cdn.example/voz.ogg
you       location:-32.9468,-60.6393
you       button:menu_reclamos|Reclamos
you       document:https://cdn.example/dni.pdf|dni.pdf
wait      8h
```

Four transcripts: thirty turns with one resident covering most of what the agent does, one
about identifiers, one with a model behind it, one where the model is unreachable.

A meta-test walks the enums and fails if any topic, action, outcome or identifier appears
in no conversation. Adding a domain with nobody talking to it is a failing test.

Nothing in a transcript varies between runs — message ids come off a counter, the clock
moves by whole minutes, timings are not written down — and no value a resident gave is in
one. What is written is the *names* of what the agent knows.

## The boundaries

[`ModuleBoundariesTest`](../../src/test/java/com/municipality/agent/architecture/ModuleBoundariesTest.java)
turns the claims in [architecture](architecture.md) into something that fails: the domain
does not import Spring or JPA, it does not name an adapter, adapters do not name each
other, no two packages point at each other, and nobody asks the wall what time it is.

Every one of those rules found a real violation the first time it ran.

## What is not tested here

**A real model.** Nothing in the suite reaches the network. The tests that exercise
`ModelClassifier` fake the model and let the answer travel through the real Spring AI
client and the real structured-output conversion — which is where an answer actually comes
apart. What a real model *decides* is not a property this suite can assert.

**The image.** CI builds it, starts it, and sends it a message, because "the image builds"
and "the image works" are different claims. That runs in
[ci.yml](../../.github/workflows/ci.yml), not in `mvn verify`.

**Load.** There are no performance tests. The shape of the thing — stateless, one row per
resident, every query by primary key — is in [architecture](architecture.md), and that is
an argument rather than a measurement.

## Coverage, honestly

The gate is 98% of lines. Nine lines are uncovered, and they are the ones that cannot be
reached: a `catch` for a `GeneralSecurityException` from an algorithm every JVM is required
to have, a branch in a Spanish renderer that an earlier guard already caught. The only
exclusion in the configuration is the `main` method that hands off to Spring.

A number that high is not the point on its own — coverage says which lines ran, not whether
anybody asserted anything about them. What it bought here was the paths nobody exercises on
purpose: a provider reporting negative token counts, a stored receipt written by a version
that shaped it differently, two instances inserting the same row. Those run on the worst
day, and until they were covered none of them had ever run at all.
