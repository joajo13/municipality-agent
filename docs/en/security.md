# Security

*This document is the source. `docs/es/security.md` is a translation of it — see
[translations](translations.md).*

---

## What this service is holding

A resident writes to a municipality over a messaging channel. The agent reads the message,
works out what they want, and asks for whatever the procedure needs. Three things pass
through it that are worth an attacker's time:

| Asset | Where it lives | Why somebody would want it |
|---|---|---|
| The resident's identity | `conversation_entity.entity_value` — document and claim numbers | It identifies a person to the municipality. A wrong one files a procedure under somebody else's name. |
| Who is talking to the municipality | `conversation.user_id`, a phone number | Knowing that a given number asked about a health procedure is itself the disclosure. |
| The model account | `ANTHROPIC_API_KEY` | Somebody else's tokens, billed to this municipality. |

Everything else — the topics, the actions, the decision rules — is public information about
how a municipal service works.

## Where the boundaries are

```
   resident ──▶ messaging provider ──▶ [1] ──▶ agent ──▶ [2] ──▶ database
                                                 │
                                                 └──▶ [3] ──▶ language model
                                                 │
                                                 └──▶ [4] ──▶ logs, metrics, traces
```

1. **The channel to the endpoint.** Untrusted. Anything on this side is attacker-shaped:
   the body, the headers, the resident id, the message id.
2. **The agent to its database.** Trusted, on a private network, credentials from the
   environment.
3. **The agent to the model.** The only place a resident's words leave this process.
4. **The agent to everything that watches it.** Wider audience and longer memory than the
   service itself: log aggregation is read by people who were never given access to the
   database.

## STRIDE

| | Threat here | What is in the way |
|---|---|---|
| **Spoofing** | Anybody posting messages as the channel | Shared secret in `X-Api-Key`, compared in constant time (`ApiKeys`). No key configured means the service does not start. |
| | The channel posting as any resident it likes | Not prevented. The channel is trusted to say who is talking; that trust is the design. A compromised channel is a compromised municipality. |
| **Tampering** | Changing what a procedure is started with | Identifiers are read by shape and by the label in front of them (`PatternEntityExtractor`), never inferred. A bare number is only read as the one thing that was asked for. |
| | Injection into the database | Every statement is parameterised: JPA and named-parameter JPQL, no string building anywhere near SQL. |
| | Schema changed underneath the service | Flyway owns the schema; Hibernate is set to `validate`, so drift stops the service instead of being patched under it. |
| **Repudiation** | "I never asked for that" | Every turn is written with its trace id, the resident's pseudonym, what was decided and what it cost. |
| **Information disclosure** | Identity in logs, metrics or traces | Nothing a resident wrote is recorded. Logs carry an HMAC pseudonym, the *names* of what is known, and numbers (`Turns`). Responses carry names, never values (`TurnResponse`). |
| | Identity reaching the model | Every long number is replaced before the call (`Confidential`). No number a resident types helps decide what a message is about. |
| | Metrics telling an outsider how many residents there are | `/actuator/**` needs the key. Only the probes are public, because an orchestrator cannot hold a secret. |
| | Stack traces naming libraries and versions | `server.error.include-stacktrace: never`, and every error is a problem detail that does not quote what was sent. |
| **Denial of service** | One integration in a loop, billed per message | Per-resident rate limit (`RateLimiter`), and a body-size limit before anything is parsed (`RequestSize`). Both are per instance and say so; the exact ones belong in a gateway. |
| **Elevation of privilege** | A prompt that talks the agent into starting a procedure | A model chooses from two enums and a number, and nothing else. What happens next is `Policy`, which is ordinary Java. The worst a hostile message achieves is the wrong topic. |
| | An unlisted path being reachable | `anyRequest().denyAll()`. A path added tomorrow is closed until somebody says otherwise. |

## The one worth spelling out: prompt injection

A resident can write anything, including instructions. Four things make that uninteresting
here:

1. **The model is asked for a shape, not for an action.** It answers with a topic, an
   action and a number. There is no field in which it could say "start this procedure".
2. **Nothing it returns is trusted.** A topic that does not exist, a missing field, a
   confidence of 4.0 — all of them come back as "nothing was understood", which is the
   menu (`ModelClassifier`).
3. **The rules are not in the prompt.** What a procedure requires, and whether it can start,
   is `Domain.requires` and `Policy`. No sentence a resident writes changes them.
4. **The instructions are not in the user message.** The prompt and the schema are in the
   system message; the user message is the resident's words and nothing else, which leaves
   less for a resident to answer back to.

The residual risk is a misroute: a message crafted to be read as a complaint when it was
about a licence. Cost: one wrong topic on one turn, corrected by the next message.

## Findings, and what happened to them

The review that produced this table was run against the whole branch. Everything at medium
or above was fixed rather than accepted.

| # | Finding | Severity | Status |
|---|---|---|---|
| 1 | With no key configured, one was generated and printed at startup. In a deployment where nobody set `API_KEY`, the credential for the endpoint ends up in the log pipeline, readable by everybody with a dashboard. | **High** | **Fixed.** The service now refuses to start without a key. The generating behaviour moved behind `agent.api.allow-generated-key`, on in the console and test profiles and in the compose file, off everywhere else. |
| 2 | The resident's message was sent to the model verbatim, document number included. | **Medium** | **Fixed.** `Confidential` replaces every long number on the way out. Numbers do not help classification, so nothing is lost. |
| 3 | A failed classification logged the exception's `toString()`. The message of a parse failure quotes the text that failed to parse, which is a model's answer about a resident's message. | **Medium** | **Fixed.** Only the exception type is logged; the whole thing is at `DEBUG`, for a machine where somebody has decided that is acceptable. |
| 4 | No limit on request body size: a body arrives, the parser reads all of it into memory, and the process is gone before a field has been looked at. | **Medium** | **Fixed.** `RequestSize` refuses a declared length over the limit and stops a chunked body that goes past it. |
| 5 | Error responses could carry stack traces, naming libraries and versions. | **Low** | **Fixed.** Turned off explicitly rather than relying on the framework default. |

## Accepted, and why

These are open, understood, and low.

**Identity is stored in plaintext.** `conversation_entity.entity_value` holds document
numbers as they were given. Encrypting them in the column would put the key in the same
process that holds the data, which moves the problem rather than solving it. What is
actually in the way: the values live in their own table with their own grant, nothing reads
them but this service, and the nightly sweep deletes them after thirty days. Encryption at
rest belongs to the database, and is the deployment's job.

**A caller with the key can post as any resident.** The channel is trusted to say who is
talking, which is what a channel is. Narrowing this means the messaging provider signing
each message, which is a change to the provider and not to this service.

**The rate limit counts per instance.** Three instances allow three times the limit. It is
there to stop a runaway loop, and a limit three times too generous stops one just as well.
An exact limit is a gateway's job.

**H2 ships in the image.** It is what makes the service start with nothing configured.
The console — the part of H2 with the history of remote code execution — is not on the
classpath, and `DB_URL` in any real deployment points at Postgres.

**Traffic to the model leaves the network.** Text with the numbers taken out still says
what somebody is asking for. A municipality that cannot accept this runs a model inside its
own network; the classifier is handed a `ChatClient` and does not know the difference.

## Running the checks

```bash
make security                        # dependencies against the vulnerability database
./mvnw -Psecurity verify             # the same thing
```

`NVD_API_KEY` makes the download minutes instead of tens of minutes. The nightly
[security workflow](../../.github/workflows/security.yml) runs this, CodeQL with the
extended queries, and a scan of the image, and reports all three into the repository's
security tab.

Anything below CVSS 7 is a finding to triage rather than a reason nothing can ship; at 7
and above the build stops. Suppressions live in
[`security/dependency-check-suppressions.xml`](../../security/dependency-check-suppressions.xml)
and every one of them needs a reason and a date to look again — a suppression with no
reason is a vulnerability somebody decided to stop being told about.

## Reporting something

Open a security advisory on the repository rather than an issue. An issue is a public
description of how to attack a service that is running.
