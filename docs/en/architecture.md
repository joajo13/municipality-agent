# Architecture

*This document is the source. `docs/es/architecture.md` is a translation of it — see
[translations](translations.md).*

---

## One turn

Everything this service does happens inside one function. A message arrives, and:

```
IncomingMessage
      │
      ▼
  Normalizer ─────────▶ NormalizedMessage      one line of text, whatever arrived
      │                                        (a photo, a voice note, a pin, a tap)
      ▼
  Conversations ──────▶ Conversation           what is remembered about this resident
      │
      ▼
  EntityExtractor ────▶ Map<EntityType,String> what this message handed over
      │
      ▼
  Classifier ─────────▶ Classification         the topic, the action, and what it cost
      │
      ▼
  Conversation.read ──▶ Intent                 the same, read against what was asked
      │
      ▼
  Policy ─────────────▶ Decision               one of five, and no sixth
      │
      ▼
  Conversations.save ─▶ Conversation           the turn, written
      │
      ▼
  Outcome                                      the answer, and how it was arrived at
```

The order is the design, and two parts of it are worth saying out loud.

**What the resident gave is read before what they meant is decided.** A message may be
nothing but an answer to the last question — "4471" is not a topic — and reading it as one
is what makes a second turn mean anything.

**The policy is asked last and is the only thing allowed to act.** A model decides a topic,
an action and a number. What the municipality does about that is `Policy`, which is
ordinary Java: the rules are applied the same way every time, and nothing a model returns
can talk it into starting a procedure it should not.

## Ports and adapters

The centre of this is plain Java with no framework in it. That is checked rather than
claimed: [`ModuleBoundariesTest`](../../src/test/java/com/municipality/agent/architecture/ModuleBoundariesTest.java)
fails the build if the domain ever imports Spring, if it names an adapter, or if two
packages point at each other.

| Port | What it is for | What implements it |
|---|---|---|
| `Classifier` | Working out what a resident wants | `KeywordClassifier` (a word list, free), `ModelClassifier` (a model, billed) |
| `Conversations` | What is remembered between turns | `InMemoryConversations` (a map), `JpaConversations` (a table) |
| `Receipts` | Which messages have already been answered | `InMemoryReceipts`, `JpaReceipts` |
| `EntityExtractor` | Reading identifiers out of a message | `PatternEntityExtractor` |
| `MediaDescriber` | Turning a voice note or a photo into text | `NoMediaDescriber` — nothing real yet |

Which implementation runs is decided in one file,
[`AgentConfig`](../../src/main/java/com/municipality/agent/AgentConfig.java), from
configuration alone. There is not one Spring annotation in the message, router, policy,
conversation, extraction or delivery packages.

## The packages

```
com.municipality.agent
├── Agent                 one turn, from what arrived to what to do about it
├── Turns                 the same turn, with a span, a log line and the counters
├── AgentConfig           where every part is chosen and joined up
├── Outcome               everything one turn produced
│
├── message/              what arrived, and how it becomes one line of text
├── router/               what they are asking for, and what it needs
├── policy/               what the agent does about it
├── conversation/         what is remembered between turns
├── extraction/           what the resident handed over
├── delivery/             which messages have already been answered
│
├── ai/                   the model, through Spring AI
├── persistence/          the tables
├── api/                  the endpoint a channel posts to
├── console/              a REPL, and decisions put into words
└── observability/        what a turn cost, and who it was, written down safely
```

The top six are the municipality. The bottom five are the outside world. `Agent` and
`Turns` are the seam: `Agent` is the rules joined up, `Turns` is the same thing with
everything that watches it wrapped around the outside.

## Exhaustive by construction

Two types in this system are `sealed`, and both of them are load-bearing:

- **`MessageContent`** — text, audio, image, document, location, a tapped button. Every
  consumer switches over it with no `default`.
- **`Decision`** — start the procedure, ask for what is missing, answer, offer a menu, put
  a person on. Same rule.

Adding a seventh kind of content, or a sixth decision, is a compile error at every place
that has to say what it means. That is the point: the alternative is a silent fallthrough
in production, in a branch nobody wrote a test for because nobody knew it existed.

The same idea runs through the prompt. `ClassificationPrompt` is assembled from the
`Domain` and `Action` enums rather than kept in a file of text, so a topic the code knows
about cannot be one the prompt forgot to mention — a file cannot fail to compile.

## State, and two instances of it

The agent holds nothing. Everything it remembers goes through `Conversations`, which means
a second instance answers the same message the same way, and it means two turns of one
conversation can race.

They cannot both win. A conversation carries `turns`, which is both how many turns have
been written and the value the next write is conditional on:

```
turn A reads turns=4 ──┐
                       ├──▶ both decide, both write "where turns = 4"
turn B reads turns=4 ──┘        one succeeds, one raises ConcurrentTurn
```

In the table there is a second counter, `version`, kept by Hibernate, for the narrower case
of two writes that read the same row and are both in flight. Both surface as the same
`ConcurrentTurn`, because from above they are the same fact: this turn was built on
something that has since moved. The endpoint turns that into `409`, and the caller sends
the message again.

Redelivery is the other half. Messaging providers resend — a timeout on their side, an
acknowledgement lost rather than a request — so every answered message leaves a receipt
keyed by the provider's own message id. A second delivery is a lookup, not a second turn,
and gets the answer the first one was given.

## Time

Nothing in this system reads a wall clock, and that is enforced by the boundary tests.

- A **turn** takes its time from the message that arrived. "Eight hours later" is an
  argument, which is what makes conversation expiry a line in a test rather than a suite
  that only passes tomorrow.
- The **sweep** and the **console**, which no message triggers, are handed the one injected
  `Clock`.
- Measuring how long something took is not asking a wall clock, so `System.nanoTime` is
  allowed and is what the cost trace uses.

## What it would take to make this several services

Nothing here needs splitting today. If it ever does, the lines are already drawn, and they
are the ports:

| Service | What moves | What is left behind |
|---|---|---|
| **Intake** | `api`, `delivery` | Posts messages onto a queue instead of calling `Turns` |
| **Understanding** | `ai`, `router`, `extraction` | `Classifier` becomes a call rather than a method |
| **Decision** | `policy`, `conversation`, `persistence` | Already the only part that writes anything |

Three things make that a move rather than a rewrite. The rules do not import a framework,
so they travel. The state is already outside the process, so two of anything already work.
And every boundary is already an interface with a test written against the interface rather
than the implementation — `ConversationsContract` and `ReceiptsContract` are what a new
implementation has to pass, whatever is behind it.

What would have to be faced on the way: a queue makes redelivery normal rather than rare
(the receipts already handle it), and a network between the decision and its state makes
the conditional write a round trip that can fail in a new way (`ConcurrentTurn` already
exists for it).

## Decisions

The reasoning behind the larger choices is in [`adr/`](adr/), one file each:

- [0001 — Sealed types over a default branch](adr/0001-sealed-types.md)
- [0002 — The model decides a shape, the rules decide everything else](adr/0002-model-decides-a-shape.md)
- [0003 — Turns as the version](adr/0003-turns-as-the-version.md)
- [0004 — Golden transcripts as the specification](adr/0004-golden-transcripts.md)
- [0005 — Identifiers do not leave the process](adr/0005-identifiers-stay.md)
