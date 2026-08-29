# Running it

*This document is the source. `docs/es/operations.md` is a translation of it — see
[translations](translations.md).*

---

## In one command

```bash
cp .env.example .env
docker compose up --build
```

That is the service, a Postgres beside it, the schema migrated, and an API key generated
and printed in the log. Add `--profile tracing` for a Jaeger at `localhost:16686`.

Without Docker:

```bash
make run        # the service on :8080, with an embedded database
make console    # the same agent, in a terminal, no HTTP
```

Both need **JDK 25**. Everything else the Maven wrapper handles.

## Configuration

Every setting has a default that works. Nothing has to be set to start — except the API
key, which has to be set anywhere that matters.

### The endpoint

| Property | Environment | Default | |
|---|---|---|---|
| `agent.api.key` | `API_KEY` | — | The shared secret. **Required**: with nothing here the service does not start. |
| `agent.api.allow-generated-key` | `ALLOW_GENERATED_KEY` | `false` | Makes one up and prints it instead of refusing to start. For a local look around, and nowhere else. |
| `agent.api.messages-per-window` | `RATE_LIMIT` | `20` | Per resident, per window, per instance. |
| `agent.api.window` | — | `1m` | |
| `agent.api.max-request-bytes` | — | `65536` | Larger bodies are refused before they are read. |

### Conversations

| Property | Environment | Default | |
|---|---|---|---|
| `agent.idle-timeout` | — | `30m` | How long a conversation stays open with nothing said. Past it the next message starts from nothing. |
| `agent.retain-for` | — | `30d` | How long a conversation is kept before it is deleted outright. |
| `agent.keep-receipts-for` | — | `2d` | How long an answered message is remembered so a redelivery gets the same answer. |
| `agent.sweep-cron` | — | `0 0 3 * * *` | When the deleting runs. |
| `agent.store` | — | `jpa` | `memory` for a run with no database at all. One instance, forgotten on restart. |
| `agent.pseudonym-secret` | `PSEUDONYM_SECRET` | — | What residents are named after in logs. Unset means a new one per run. |

### The database

| Property | Environment | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | An embedded H2 in PostgreSQL mode |
| `spring.datasource.username` | `DB_USERNAME` | `sa` |
| `spring.datasource.password` | `DB_PASSWORD` | empty |
| `spring.datasource.hikari.maximum-pool-size` | `DB_POOL_SIZE` | `10` |

Flyway owns the schema and runs at startup. Hibernate is set to `validate`, so a schema
that has drifted stops the service rather than being patched under it.

### The model

Nothing is billed until a model is configured. Without one, the keyword stand-in answers
and says so at startup.

```bash
SPRING_PROFILES_ACTIVE=ai ANTHROPIC_API_KEY=... make run
```

| Property | Environment | Default |
|---|---|---|
| `spring.ai.anthropic.api-key` | `ANTHROPIC_API_KEY` | — |
| `spring.ai.anthropic.chat.options.model` | — | `claude-haiku-4-5` |
| `agent.pricing.currency` | — | `USD` |
| `agent.pricing.models.<id>.input-per-million` | — | see `application.yaml` |
| `agent.pricing.models.<id>.output-per-million` | — | see `application.yaml` |

Prices are keyed by the model id **the provider reports on its own response**, not the one
that was asked for — those differ the moment a provider aliases a name to a dated version.
A model with no price is counted as costing nothing, says so once at warning level, and is
tagged `priced=false` on its metric. Check the counter after changing models.

### Watching it

| Property | Environment | Default |
|---|---|---|
| `management.tracing.sampling.probability` | `TRACING_SAMPLE_RATE` | `1.0` |
| `management.otlp.tracing.export.enabled` | `OTLP_ENABLED` | `false` |
| `management.otlp.tracing.endpoint` | `OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` |
| `logging.structured.format.console` | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | plain text |

## What to watch

| Metric | Tags | |
|---|---|---|
| `agent.turns` | `domain`, `action`, `decision` | Every turn. A rising share of `FallbackMenu` is the agent understanding less than it used to. |
| `agent.model.calls` | `model`, `priced` | `priced=false` means the bill is being understated. |
| `agent.model.tokens` | `model`, `direction` | |
| `agent.model.cost` | `model`, `currency` | The day's spend, without waiting for the invoice. |
| `http.server.requests` | `uri`, `status` | 409s are conversations racing; 429s are somebody looping. |
| `hikaricp.connections.pending` | | Threads waiting for a connection. |

Scraped from `/actuator/prometheus`, which needs the API key.

Every turn also writes one line:

```
turn=2 intent=RECLAMOS/CHECK_STATUS confidence=0.92 decision=StartFlow
given=[CLAIM_NUMBER] took=318ms cost=0.000502 USD
```

with `traceId` and `resident` in the logging context — `resident` being an HMAC pseudonym,
never the phone number. Nothing a resident wrote is in there.

## Probes

| Path | Public | |
|---|---|---|
| `/actuator/health/liveness` | yes | Is the process wedged. A database that went away is not a reason to restart it. |
| `/actuator/health/readiness` | yes | Can it answer — includes the database. |
| `/actuator/health`, `/actuator/info` | yes | |
| `/actuator/prometheus` | no | Metrics say how many residents there are and what the day cost. |

## When something is wrong

**Every message gets the menu.** The model is unreachable or the key is rejected. Look for
`Could not classify message` at `WARN`; the trace id in that line is the message. The agent
is doing the right thing — it says it did not understand rather than guessing — but nobody
is being helped. Check `ANTHROPIC_API_KEY` and outbound network.

**409s in the access log.** Two messages from one resident being handled at once. Normal in
small numbers. A steady stream means the channel is redelivering without a stable
`messageId`, so nothing is being recognised as a redelivery.

**The service will not start, `Schema validation`.** The database is not the schema the
code expects. Either a migration did not run, or something else is writing to that
database. Do not set `ddl-auto`; find out which.

**The service will not start, `No API key is configured`.** Working as intended. Set
`API_KEY`.

**Costs rising with no more traffic.** Check `agent.model.calls{priced=false}` first — a
model id changed under a price list that did not. Then the tokens per call: a longer prompt
is a bigger bill on every message.

**A resident says the agent forgot them.** Conversations expire after `agent.idle-timeout`
and are deleted after `agent.retain-for`. Both are deliberate: a document number given
yesterday is not permission to file something with it today.

## Scaling, and what breaks first

Instances are interchangeable and hold nothing. Add them.

One thing counts per instance and is therefore an approximation at any number above one:
the rate limit. Three instances allow three times it. That is documented in
[security](security.md) as belonging to a gateway when an exact answer is needed.

The database is the shared thing. One row per resident, one per answered message, both
swept nightly, and every query is by primary key.
