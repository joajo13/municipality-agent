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

**No static I/O.** `ConsoleRunner` receives a `Reader` and a `PrintWriter` through its
constructor rather than reaching for `System.in` and `System.out`. Its tests drive the whole
loop as plain Java over a `StringReader`, with no Spring context and no output capture.

**Tests that do not need the container.** Starting an application context is reserved for
the one test whose subject *is* the context. Everything else is exercised directly.

## Roadmap

- [x] Toolchain: JDK 25, Maven project
- [x] Spring Boot baseline
- [x] Console REPL — echoes input, no agent behind it yet
- [ ] **Message, routing and policy model, driven by a stubbed classifier** ← in progress
- [ ] Real classifier on Spring AI
- [ ] Entity extraction and open-question tracking

## Layout

```
src/main/java/com/municipality/agent/
├── MunicipalityAgentApplication.java   Spring Boot entry point
├── console/                            the REPL and its I/O wiring
├── message/                            what arrived, and how it becomes readable text
└── package-info.java                   @NullMarked (JSpecify) per package
```

## Build and run

Requires **JDK 25**. The Maven wrapper handles the rest.

```bash
./mvnw test              # run the test suite
./mvnw spring-boot:run   # start the console REPL, type 'exit' to quit
```

## Status

Step 4 is underway. The message model is in place: whatever a resident sends — text, a voice
note, a photo with a caption, a shared pin — collapses into the one line a classifier will
read, with media announced as a placeholder until something is there to transcribe or look
at it. Routing and policy come next, and none of it is wired to the REPL yet: it still echoes.
