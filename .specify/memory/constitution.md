<!--
Sync Impact Report
------------------
Version change: 1.7.1 -> 1.8.0
Modified principles:
  - VI. Documentation: method documentation MUST also cite the requirements the
    method participates in implementing (functional-requirement or
    success-criterion identifiers, such as FR-### or SC-###)
Added sections: none
Removed sections: none
Follow-up TODOs: none
-->

# SIMULA Constitution

## Core Principles

### I. Test-First (NON-NEGOTIABLE)
Test-Driven Development is mandatory. Tests are written before the implementation
they validate, in this strict order: tests written -> user approved -> tests fail
with a clear reason -> implementation added. The Red-Green-Refactor cycle is
strictly enforced and no production code may be merged without its accompanying
test written first.

### II. Coverage Standard
Every deliverable MUST achieve a line coverage and a branch coverage of at least
97%. Coverage is measured by an automated coverage tool against the code under
test; a merge or release is blocked when either metric falls below the threshold.
Rationale: branch coverage prevents the common failure of hitting every line while
leaving conditional paths untested.

### III. English Code
All source code, identifiers, function names, variable names, string literals, and
file names MUST be written in English. No non-English terms may appear in code
unless they are domain-specific proper nouns that cannot be translated.
Rationale: English maximizes readability and collaboration across the team and
tooling.

### IV. English Comments
All comments, documentation strings, and inline annotations MUST be written in
English. Comments MUST explain the rationale and intent, not merely restate the
code. Rationale: comments form part of the durable documentation surface and must
be equally understandable by every contributor.

### V. Formatting
All code MUST conform to the project's automated formatter (e.g., Prettier,
Black, rustfmt, or the project-declared equivalent). Formatting is applied before
a change is submitted, and any formatted diff is enforced by the quality gate.
Rationale: consistent formatting removes review noise and keeps diffs focused on
behavioral change.

### VI. Documentation
API documentation is mandatory for every package, every class, and every method
regardless of visibility (public, protected, or private). Each project MUST use
the documentation tool native to its implementation language — **Javadoc** for
Java, or the **equivalent tool** for other languages (e.g. Doxygen, rustdoc,
JSDoc, Sphinx/pydoc, godoc, docstrings) — and the documentation MUST describe the
purpose and contract of the element, including parameters, return values, and
thrown exceptions where applicable, and MUST be written in English. For each
method, the documentation MUST also cite the requirements the method participates
in implementing (for example the functional-requirement or success-criterion
identifiers, such as FR-### or SC-###).
Rationale: exhaustive API documentation makes APIs self-describing and keeps
internal behavior understandable, reducing the need for external explanation.

### VII. Named Literals (NON-NEGOTIABLE)
All string and numeric literals MUST be declared as named constants. Raw
literals (magic values) are forbidden in code. The integer literals `-1`, `0` and
`1` are the sole exceptions and MAY be written inline; every other literal — a
message, a size, a code, a threshold, a tag number, a default value, etc. — MUST
be assigned to a named constant (e.g. a `static final` field, an enum constant,
or a named compile-time constant) and referenced by that name.
Rationale: named constants make intent explicit, prevent drift between duplicated
values, and keep security-relevant and format-specific values traceable and
single-sourced.

### VIII. Architecture Document
The project MUST maintain a current architecture document describing the system
as it is implemented. The document MUST be updated regularly so that it always
reflects the current state of the architecture — its packages, components,
formats, processes, dependencies, and key decisions.
- The architecture document MUST contain no historical information: it MUST NOT
  record past states, superseded decisions, change logs, or migration history. It
  describes only the architecture as it exists now.
- Any architectural change (new component, changed process, altered dependency,
  or revised decision) MUST be reflected in the architecture document as part of
  the change that introduces it, so the document never falls out of date.
- All structural diagrams (context/dataflow, package structure, data-model
  relationships, process/sequence flows, and component wiring) in the
  architecture document MUST be expressed as **Mermaid** diagrams (e.g.
  `flowchart`, `classDiagram`, `sequenceDiagram`) inside fenced Mermaid blocks
  with the `mermaid` language tag. ASCII-art or text-drawn diagrams are not
  permitted for structural content.
- An `architecture.md` document MUST be maintained per functional branch (for
  example `specs/001-stanag-metadata-codecs/architecture.md`) and MUST be kept
  up to date with the current architecture, with no historical information
  recorded in it.
Rationale: the architecture document is the durable, living reference for how the
system is built today. Keeping it current and free of historical detail prevents
it from accumulating obsolete or misleading content and ensures it stays a
reliable entry point for contributors. Mermaid keeps diagrams machine-readable,
version-controllable, and uniformly rendered.

## Additional Constraints

### Quality Gates & Compliance
- Coverage MUST be measured and reported for both lines and branches on every
  change; the 97% threshold applies to both metrics independently.
- A change that fails any gate (coverage, formatting, or linting) MUST NOT be
  merged or deployed until the failure is corrected and the gate passes.
- Test suites MUST be runnable with a single command and MUST be deterministic:
  the same input always produces the same result.
- New dependencies MUST NOT be introduced without an approved justification; each
  dependency MUST itself satisfy the same code, comment, and formatting standards.

## Development Workflow

### Test-First Workflow
- Start every feature or fix by authoring the tests that define the expected
  behavior, following the test-first order described in Principle I.
- After the user approves the tests, confirm they fail for the intended reason
  before writing any implementation (Red).
- Implement the minimal code required to make the tests pass (Green), then
  refactor while keeping the suite green (Refactor).
- Run the full test suite, verify the 97% line and branch coverage thresholds, and
  apply the formatter before submitting the change for review.

## Governance
This constitution supersedes all other practices, guidance, and ad-hoc decisions
within this project. Any deviation from these principles MUST be documented,
justified, and approved before it is accepted.

- Amendments: proposed changes MUST be documented, approved by the project owner,
  and recorded here with an updated version and amendment date.
- Versioning: the version is bumped per semantic versioning rules. A MAJOR bump
  indicates a backward-incompatible principle change, a MINOR bump adds a
  principle or materially expands guidance, and a PATCH bump records
  clarifications or non-semantic refinements.
- Compliance review: every pull request and release MUST be checked against these
  principles; coverage, formatting, documentation, and language standards are
  enforced automatically where tooling allows.
- Runtime guidance for day-to-day development is referenced from
  [GUIDANCE_FILE] when present.

**Version**: 1.8.0 | **Ratified**: 2026-09-18 | **Last Amended**: 2026-09-22
