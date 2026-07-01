# Essay Review Skill

Use this skill when reviewing Kaspel Essayist draft or published Markdown essays, especially when the change touches the essay agent workflow, DICE context handling, or Dokimos evaluation gates.

## Inputs

- The draft or published Markdown essay.
- The requested topic.
- The DICE context capsule, if available.
- The Dokimos evaluation report, if available.

## Review Steps

1. Confirm the essay directly answers the requested topic in the title, first section, and conclusion.
2. Check that at least one DICE proposition is preserved in the essay body without contradicting the context capsule.
3. Confirm research-brief claims appear as grounded statements rather than unsupported assertions.
4. Verify the Markdown artifact has front matter, a TLDR block, section headings, and readable paragraphs.
5. Use the Dokimos report as a deterministic gate: failed checks should become concrete revision notes.

## Output

Return a compact review with these fields:

- `status`: `pass`, `revise`, or `block`.
- `summary`: one sentence explaining the decision.
- `required_changes`: specific edits needed before publishing.
- `evidence`: short references to the title, section heading, context proposition, or Dokimos check that drove the decision.
