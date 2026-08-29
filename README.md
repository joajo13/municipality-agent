# municipality-agent

*[Leer en castellano](LEEME.md)*

A conversational agent for municipal services: it reads what a resident writes, works out
which procedure they are after, asks for whatever identification that procedure requires,
and remembers what it has been told.

**Java 25 · Spring Boot 4 · Spring AI · PostgreSQL**

---

## About this repository

This is a second implementation. The same agent already exists and runs in production as a
Node/TypeScript service, covering fourteen procedure domains over WhatsApp — intake,
routing, entity extraction, retrieval and human handoff included.

This repository rebuilds that system on the JVM, starting from an empty directory. The
problem is settled; the stack is not. That makes it a clean baseline for the questions a
port actually forces:

- Which parts of the original design were TypeScript idioms, and which were the domain talking?
- What does a discriminated union become in a language that offers sealed interfaces and records instead?
- Where does dependency injection replace hand-wired composition, and where does it only add indirection?

Nothing is transliterated. Every type here is re-derived from the problem and then checked
against the original, which is why the two models differ in places.

## Running it

```bash
cp .env.example .env
docker compose up --build
```

That is the service on `:8080`, a Postgres beside it, the schema migrated, and an API key
generated and printed once in the log. Then:

```bash
curl -sS localhost:8080/api/v1/messages \
  -H "X-Api-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"from":"+5493415551234",
       "contents":[{"type":"text","body":"quiero consultar el estado de mi reclamo"}]}'
```

Without Docker, with **JDK 25**:

```bash
make run        # the service, with an embedded database
make console    # the same agent in a terminal
make verify     # the suite, the coverage gate, the module boundaries
```

Nothing needs an API key or a network to start. With no model configured, a keyword
stand-in classifies and says so; the `ai` profile puts a real one behind it.

## What it looks like

```
you > se rompio una luminaria en Sarmiento 450

  turno      1
  texto      se rompio una luminaria en Sarmiento 450
  intent     RECLAMOS / START_PROCEDURE  (1.0)
  decision   StartFlow RECLAMOS / START_PROCEDURE

bot > Listo, arranco el trámite de reclamos.

you > quiero consultar el estado de mi reclamo

  turno      2
  intent     RECLAMOS / CHECK_STATUS  (1.0)
  decision   AskFor [CLAIM_NUMBER]

bot > Para seguir necesito el número de reclamo.

you > 4471

  turno      3
  intent     RECLAMOS / CHECK_STATUS  (1.0)
  decision   StartFlow RECLAMOS / CHECK_STATUS
  recibido   [CLAIM_NUMBER]
  recordado  [CLAIM_NUMBER]

bot > Listo, arranco el trámite de reclamos.
```

Filing a complaint needs nothing, so it starts. Asking after one needs its number, which
nobody has given, so it asks. "4471" is not a topic — it is the answer to the question on
the table, and that is the only reading of it that is not a guess.

The trace above the reply is deliberate: a decision that looks right for the wrong reason
is indistinguishable from one that is right, and that difference is what the console is
for. It never prints a value the resident gave, only the names of what is known.

## Design notes

**The domain comes before the model.** The language model arrives at step 5 of 6. Message,
routing and policy types are built and tested against a stubbed classifier first, so that
the prompt ends up being a detail of the system rather than its architecture.

**Exhaustive by construction.** Message content and decisions are sealed interfaces, and
consumers switch over them with no `default` branch. Adding a variant is a compile error at
every site that has to handle it, instead of a silent fallthrough at runtime. The
classification prompt is assembled from the same enums, so a topic the code knows about
cannot be one the prompt forgot to mention.

**Nothing the model says is trusted.** It answers with a topic, an action and a number, and
all three are checked before anything downstream sees them. A topic that does not exist, a
timeout, a rejected key — all one event, all read the same way, all ending at the menu
rather than at a crash or a guess.

**Identifiers do not leave the process.** Every long number is taken out of a message before
it reaches the model: no number a resident types helps decide what they are asking for. Logs
and API responses carry the *names* of what is known, never the values.

**Two instances answer the same message the same way.** The agent holds no state.
Conversations are written conditionally on the turn they were read at, so two messages from
one resident racing on two instances cannot both win, and the one that loses is told rather
than dropped.

**Tests that do not need the container.** Starting an application context is reserved for
tests whose subject *is* the context. Everything else is exercised directly — and whole
conversations are exercised as [transcripts](docs/en/testing.md), where the file is both the
script and the expectation.

## Documentation

Everything below is in English and [castellano](docs/es/), kept in step by
[`scripts/translations`](scripts/translations) and checked on every build.

| | |
|---|---|
| [Architecture](docs/en/architecture.md) | One turn, the ports, the packages, and what it would take to split this into services |
| [The endpoint](docs/en/api.md) | What to send, what comes back, and every way it says no |
| [Running it](docs/en/operations.md) | Configuration, metrics, probes, and what to do when something is wrong |
| [Security](docs/en/security.md) | The threat model, the findings, and the risks that stay open |
| [Testing](docs/en/testing.md) | Four kinds of test and what each one is for |
| [Decisions](docs/en/adr/) | Why the larger choices are the way they are |

## Layout

```
src/main/java/com/municipality/agent/
├── Agent.java                          one turn, joined up
├── Turns.java                          the same turn, watched
├── AgentConfig.java                    where the parts are assembled
├── Outcome.java                        what one trip through the agent produced
├── message/                            what arrived, and how it becomes readable text
├── router/                             what they are asking for, and what it needs
├── policy/                             what the agent does about it
├── conversation/                       what is remembered between turns
├── extraction/                         what the resident handed over
├── delivery/                           which messages have already been answered
├── ai/                                 the model, through Spring AI
├── persistence/                        the tables
├── api/                                the endpoint a channel posts to
├── console/                            the REPL, and decisions put into words
└── observability/                      what a turn cost, and who it was, written down safely
```

## Status

The pipeline runs end to end, over HTTP and in a terminal, against a real model or a word
list. A message becomes one line of text, gets routed to a topic and an action, and becomes
one of five decisions: run the procedure, ask for what is missing, answer, offer a menu, or
put a person on. What the resident hands over is read, remembered, and asked for only once.
Every turn is traced, priced, and counted.

- [x] Toolchain: JDK 25, Maven project
- [x] Spring Boot baseline
- [x] Console REPL
- [x] Message, routing and policy model, driven by a stubbed classifier
- [x] Real classifier on Spring AI
- [x] Entity extraction and open-question tracking
- [x] Conversations in a database, safe for more than one instance
- [x] HTTP channel, idempotent against redelivery
- [x] Tracing, metrics and cost per turn
- [x] Golden conversation transcripts, 98% coverage, module boundaries enforced
- [x] Container image, compose, CI/CD, threat model
- [ ] **Transcription and vision, so a voice note is what was said** ← next
- [ ] Retrieval, so `Answer` answers from the municipality's own documentation
- [ ] Actually running the procedures a `StartFlow` decides on

The two stand-ins that remain are named in the code. Media arrives announced but undescribed
— a voice note reaches the classifier as `[audio]` — and `Answer` and `StartFlow` say what
should happen without anything behind them yet to do it.
