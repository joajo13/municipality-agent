# 2. The model decides a shape, the rules decide everything else

*Accepted.*

## Context

A language model is the only thing that can read "se rompió una luminaria en Sarmiento 450"
and know it is a complaint. It is also a component that will, on some percentage of
messages, return something that is wrong, malformed, or shaped by whatever the resident
wrote into the message.

The tempting design is to let the model do more: decide the topic, decide whether the
procedure can start, write the reply. Each step in that direction moves a municipal rule
out of code and into a prompt.

## Decision

The model answers with a topic, an action and a number, in a schema generated from the
enums themselves. Nothing else.

What a procedure requires is `Domain.requires`. Whether it can start is `Policy`. What the
resident reads is `DecisionRenderer`. All three are ordinary Java with tests.

Nothing the model returns is trusted: a topic that does not exist, a missing field, a
confidence outside 0..1, a timeout, a rejected key all come back as "nothing was
understood", which the policy turns into the menu.

## Consequences

The worst a hostile or confused message achieves is the wrong topic on one turn, corrected
by the next message. It cannot start a procedure the rules do not allow, because the model
has no way to express that.

The prompt cannot drift from the code: it is assembled from `Domain` and `Action`, so
adding a topic stops the build until somebody describes it.

The cost is that the model cannot be clever. It cannot notice that a resident asked two
things at once, or route on something the enums do not have a name for. Both would be
changes to the domain model, which is where they belong.
