# The Spanish documentation

*This document is the source. `docs/es/translations.md` is a translation of it.*

---

Everything in `docs/en/` is the source. Everything in `docs/es/` is a translation of the
file with the same name, and says so at the top:

```html
<!-- translated-from: architecture.md@3f2a9c1e -->
```

That is the first eight characters of the SHA-256 of the English file at the moment it was
translated. It is what makes a stale translation a thing the build can find rather than a
thing somebody notices a year later.

## Checking

```bash
scripts/translations check
```

Reads every file in `docs/es/`, recomputes the hash of its source, and reports any that
have moved. No network, no key, nothing to configure — which is why CI runs it on every
change. A stale translation fails the build.

## Retranslating

```bash
ANTHROPIC_API_KEY=... scripts/translations update
```

Translates every file whose source has changed since the marker was written, and only
those. The prompt asks for Rioplatense Spanish, for code, identifiers, paths and command
lines to be left exactly as they are, and for the register to be kept: these documents
argue about trade-offs, and a translation that flattens that into a manual has lost the
part worth reading.

Then read the diff. A translation is a change to the documentation, and it gets reviewed
like one.

## Why it is done this way

**English is the source** because the code, the identifiers and the commit messages are in
English, and documentation that disagrees with the identifiers it describes is worse than
documentation in one language.

**Spanish is not optional** because this is a service for an Argentine municipality. The
people who operate it, and the people who will inherit it, work in Spanish. Documentation
they have to translate in their heads is documentation that gets read once.

**The marker rather than a timestamp** because a timestamp says when somebody ran
something, and a hash says whether the thing being described has changed since.

**A model does the translating** rather than a person, and that is a real trade-off: it is
fast enough that the Spanish never falls behind, and it is not a professional translator.
The diff is reviewed by somebody who reads both.

## What is not translated

The code, its comments, the commit messages, and the conversation transcripts. Comments
sit next to the identifiers they describe and would have to be read in two languages at
once. The one thing in the codebase that writes Spanish is `DecisionRenderer`, which is
what a resident actually reads.
