# municipality-agent

A conversational agent for municipal services: it reads what a resident writes, works out
which procedure they are after, asks for whatever identification that procedure requires,
and answers from the municipality's own documentation.

**Java 25 · Spring Boot 4 · Spring AI**

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

## Design notes

**The domain comes before the model.** The language model arrives at step 5 of 6. Message,
routing and policy types are built and tested against a stubbed classifier first, so that
the prompt ends up being a detail of the system rather than its architecture.

**Exhaustive by construction.** Message content is a sealed interface, and consumers switch
over it with no `default` branch. Adding a content type is therefore a compile error at
every site that has to handle it, instead of a silent fallthrough at runtime.

**Nothing the model says is trusted.** The classifier asks for a topic, an action and a
number, and checks all three before anything downstream sees them. A topic that does not
exist, a missing field, a confidence of 4.0, a timeout, a rejected key — all one event,
and all read the same way: nothing was understood. What follows is the menu, not a crash
and not a guess.

**No static I/O.** `ConsoleRunner` receives a `Reader` and a `PrintWriter` through its
constructor rather than reaching for `System.in` and `System.out`. Its tests drive the whole
loop as plain Java over a `StringReader`, with no Spring context and no output capture.

**Tests that do not need the container.** Starting an application context is reserved for
the one test whose subject *is* the context. Everything else is exercised directly.

## Roadmap

- [x] Toolchain: JDK 25, Maven project
- [x] Spring Boot baseline
- [x] Console REPL
- [x] Message, routing and policy model, driven by a stubbed classifier
- [x] Real classifier on Spring AI
- [ ] **Entity extraction and open-question tracking** ← next

## Layout

```
src/main/java/com/municipality/agent/
├── MunicipalityAgentApplication.java   Spring Boot entry point
├── Agent.java                          the three stages, joined up
├── AgentConfig.java                    where the parts are assembled
├── Outcome.java                        what one trip through the agent produced
├── console/                            the REPL, and decisions put into words
├── message/                            what arrived, and how it becomes readable text
├── router/                             what they are asking for, and what it needs
├── policy/                             what the agent does about it
└── package-info.java                   @NullMarked (JSpecify) per package
```

## What it looks like

```
you > se rompio una luminaria en Sarmiento 450

  texto      se rompio una luminaria en Sarmiento 450
  intent     RECLAMOS / START_PROCEDURE  (1.0)
  decision   StartFlow RECLAMOS / START_PROCEDURE

bot > Listo, arranco el trámite de reclamos.

you > quiero consultar el estado de mi reclamo

  texto      quiero consultar el estado de mi reclamo
  intent     RECLAMOS / CHECK_STATUS  (1.0)
  decision   AskFor [CLAIM_NUMBER]

bot > Para seguir necesito el número de reclamo.

you > quiero hablar con una persona

  texto      quiero hablar con una persona
  intent     UNKNOWN / HANDOFF  (1.0)
  decision   Handoff UNKNOWN

bot > Te paso con una persona.
```

That session is the keyword stand-in, which is either sure or not and has no third
answer. A model fills the same line with a real number, and the policy stops at the same
threshold either way.

Filing a complaint needs nothing, so it starts. Asking after one needs its number, which
nobody has given, so it asks. And somebody who asked for a human gets one, whatever the
agent did or did not understand about the topic.

The trace above the reply is deliberate: a decision that looks right for the wrong reason
is indistinguishable from one that is right, and that difference is what this console is
for.

## Build and run

Requires **JDK 25**. The Maven wrapper handles the rest.

```bash
./mvnw test              # run the test suite
./mvnw spring-boot:run   # start the console REPL, type 'exit' to quit
```

Neither of those needs an API key or a network: with no model configured the keyword
stand-in classifies, and says so at startup. To run against the real one:

```bash
export ANTHROPIC_API_KEY=...
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai
```

The provider is Anthropic and lives in two places — one dependency and one block of
`application.yaml`. Nothing in the Java names it: the classifier is handed a Spring AI
`ChatClient` and does not know what is behind it.

## Status

The pipeline runs end to end, and what reads the message is now a model. Whatever a
resident sends — text, a voice note, a photo with a caption, a shared pin — collapses into
the one line the classifier reads, gets routed to a topic and an action, and becomes one
of five decisions: run the procedure, ask for what is missing, answer, offer a menu, or
put a person on.

The prompt is assembled from `Domain` and `Action` rather than kept in a file of text, so
a topic the code knows about cannot be one the prompt forgot to mention: adding a domain
stops the build until somebody says what it covers. The answer comes back in the shape of
those same enums, and is checked before it is believed.

Two things are still stand-ins. Media arrives announced but undescribed — a voice note
reaches the classifier as `[audio]`, not as what was said. And nothing is remembered
between turns, so a procedure that needs a dni asks for it again every time. Extraction
and memory are what comes next.
