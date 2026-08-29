# The endpoint

*This document is the source. `docs/es/api.md` is a translation of it — see
[translations](translations.md).*

---

One endpoint. A message goes in, what the agent did about it comes out.

```
POST /api/v1/messages
X-Api-Key: <the shared secret>
Content-Type: application/json
```

Everything under `/api` and everything under `/actuator` except the probes needs the key.
Anything not listed here is denied rather than allowed.

## Sending a message

```json
{
  "messageId": "wamid.HBgNNTQ5MzQxNTU1MTIzNBUCABIYE",
  "from": "+5493415551234",
  "sentAt": "2026-08-24T10:00:00Z",
  "contents": [
    { "type": "text", "body": "quiero consultar el estado de mi reclamo" }
  ]
}
```

| Field | Required | What it is |
|---|---|---|
| `messageId` | no | The provider's own id. Send the same one again and the same answer comes back without the turn happening twice. Without it, every delivery is a new message. |
| `from` | yes | Whoever sent it, as the channel identifies them. At most 128 characters. |
| `sentAt` | no | When they sent it. Defaults to now, which is right for a provider posting immediately and wrong for one replaying a backlog. |
| `contents` | yes | Between 1 and 10 pieces. |

### What a message can carry

| `type` | Fields | Notes |
|---|---|---|
| `text` | `body` | Up to 4096 characters. |
| `audio` | `url` | A voice note. Until a transcription model is wired in it reaches the classifier as `[audio]`. |
| `image` | `url`, `caption` | The caption is read; what is in the photo is not, yet. |
| `document` | `url`, `filename` | |
| `location` | `latitude`, `longitude` | |
| `button` | `id`, `title` | A tap on something the agent offered. The title is what the resident read. |

## What comes back

```json
{
  "messageId": "wamid.HBgNNTQ5MzQxNTU1MTIzNBUCABIYE",
  "reply": "Para seguir necesito el número de reclamo.",
  "decision": "AskFor [CLAIM_NUMBER]",
  "intent": { "domain": "RECLAMOS", "action": "CHECK_STATUS", "confidence": 0.92 },
  "conversation": { "turn": 1, "known": [], "awaiting": ["CLAIM_NUMBER"] },
  "usage": {
    "model": "claude-haiku-4-5",
    "inputTokens": 412,
    "outputTokens": 18,
    "cost": "0.000502",
    "currency": "USD",
    "tookMillis": 318
  }
}
```

`reply` is what the resident reads and is the only field a channel has to forward. The rest
is for whoever is integrating.

`known` and `awaiting` are lists of **names**, never values. The caller already has the
document number they sent, and this response ends up in their logs as well as ours.

`usage` is absent when no model was involved — a turn answered from the word list costs
nothing and says so. `cost` is a string because money in a JSON number is money that some
parser has had an opinion about.

`X-Idempotent-Replay: true` on the response means this is the answer from the first
delivery, not a new one.

| `domain` | `action` | `decision` |
|---|---|---|
| `SALUD`, `LICENCIAS`, `RECLAMOS`, `SMALLTALK`, `UNKNOWN` | `START_PROCEDURE`, `CHECK_STATUS`, `INFORMATION`, `HANDOFF` | `StartFlow`, `AskFor`, `Answer`, `FallbackMenu`, `Handoff` |

## When it says no

Every error is a [problem detail](https://www.rfc-editor.org/rfc/rfc9457) with a stable
`type`, and none of them quote what was sent.

| Status | `type` ends with | What happened | What to do |
|---|---|---|---|
| 400 | `unreadable-request` | The body is not a message this endpoint can read | Fix the request |
| 400 | `invalid-request` | Right shape, wrong contents. `fields` says which | Fix the request |
| 401 | `unauthenticated` | No key, or the wrong one | Send `X-Api-Key` |
| 403 | — | A path that is not served | — |
| 409 | `conversation-moved-on` | Another message from this resident was handled first | Send this one again |
| 413 | — | The body is larger than any message could be | Send less |
| 429 | `too-many-messages` | This resident is over the limit for the window | Wait; `Retry-After` says how long |

## Trying it

```bash
curl -sS localhost:8080/api/v1/messages \
  -H "X-Api-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"from":"+5493415551234",
       "contents":[{"type":"text","body":"quiero consultar el estado de mi reclamo"}]}'
```

Then answer the question it asks, as the same resident:

```bash
curl -sS localhost:8080/api/v1/messages \
  -H "X-Api-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"from":"+5493415551234","contents":[{"type":"text","body":"4471"}]}'
```

## What is not here

No endpoint reads a conversation back, deletes one, or lists anything. The service answers
messages; everything else about a resident's data is a question for the database and the
retention sweep, which is described in [operations](operations.md).
