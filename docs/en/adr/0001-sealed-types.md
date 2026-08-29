# 1. Sealed types over a default branch

*Accepted.*

## Context

Two things in this system are closed sets that consumers have to handle completely: the
kinds of content a message can carry, and the outcomes the agent can decide on. The
original implementation was TypeScript, where both were discriminated unions and the
compiler checked exhaustiveness at every `switch`.

The obvious Java translation is an interface with implementations and a `switch` with a
`default` branch. That compiles, and it is wrong in a specific way: adding a seventh kind
of content is a silent fallthrough at every site that already handled six, in production,
in a branch nobody wrote a test for because nobody knew it existed.

## Decision

`MessageContent` and `Decision` are `sealed`. Every consumer switches over them with no
`default`.

## Consequences

Adding a variant stops the build at every place that has to say what it means. That is the
whole benefit and it is worth the cost.

The cost is that the list of implementations lives in the interface, so a variant cannot be
added from outside the package. For these two types that is correct — a seventh kind of
message content is a change to what this system is, not an extension point.

One place pays for it awkwardly: `DecisionRenderer.inSpanish(Domain)` has to name every
domain even where a branch is unreachable through the public method. Left as is; the
alternative is a `default` that would swallow a new domain.
