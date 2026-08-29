# 3. Turns as the version

*Accepted.*

## Context

Two messages from one resident, arriving at two instances at the same time, is not a rare
event on a messaging channel — a person types twice, or a provider retries. Both turns read
the same conversation, both decide, both write. Without something in the way, the second
write silently replaces the first, and the resident's answer to the question they were
asked is gone.

## Decision

`Conversation.turns` counts how many turns have been written and is also what the next
write is conditional on. A turn read at 4 is written "where turns = 4". The loser gets
`ConcurrentTurn`, which the endpoint returns as `409` with "send it again".

In the database there is a second counter, `version`, kept by Hibernate, for the narrower
race of two writes that read the same row inside overlapping transactions. Both surface as
the same `ConcurrentTurn`.

## Consequences

No lost updates, and no locks held across a model call — the conditional write is a single
statement.

The losing turn is told rather than dropped, which means the caller has something to do
about it. That is the reason for the choice: a dropped message on a municipal channel is a
resident who was ignored.

A conversation that is forgotten and started again keeps its turn count, because the count
is also the version. So `turns` is "turns ever written with this resident" rather than
"turns in this conversation". The alternative was a third counter for a distinction nobody
was asking for.
