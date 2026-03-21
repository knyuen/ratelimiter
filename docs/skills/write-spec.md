---
name: write-spec
description: This skill should be used when the user asks to "write a spec", "write a specification", "create a spec", "draft a specification", "spec out a feature", or wants to document requirements for an issue or feature.
version: 1.0.0
---

# Write Spec

This skill guides writing a specification document for a feature or issue, grounded in the issue tracker and refined through clarification before writing.

## Process

Follow these steps in order. Do not skip ahead.

### Step 1 — Identify the Issue

Check whether the project has an issue tracking system:
- Look for `.github/` to detect GitHub Issues
- Look for a `jira.json`, `.jira`, or project config referencing JIRA
- Check the current git branch name with `git branch --show-current` — branch names like `feature/PROJ-123-description` or `feat/123-add-login` often encode the issue reference

**Then:**
- If an issue reference is found in the branch name, confirm it with the user: "I see the branch name suggests issue `PROJ-123` — is that the one you'd like to spec?"
- If no issue reference is detected, ask the user: "Which issue should this specification be based on? Please provide the issue number or URL."
- If no issue tracking system is detected, ask the user to describe the feature or paste any existing requirements.

### Step 2 — Fetch the Issue

Retrieve the issue contents using the appropriate tool:

- **GitHub Issues**: Use `gh issue view <number>` (or `gh issue view <number> --json title,body,labels,comments`) to read the title, description, labels, and comments.
- **JIRA**: Use the JIRA REST API or `curl` with the user's JIRA base URL: `GET /rest/api/2/issue/<issue-key>` — ask the user for their JIRA base URL and credentials/token if not already available.
- **No tracker**: Use what the user provided directly.

Read the issue carefully. Note:
- What is already specified (acceptance criteria, user stories, constraints)
- What is vague, missing, or ambiguous
- Any linked issues, PRs, or design references

### Step 3 — Clarify Gaps

Before writing, surface gaps and ambiguities to the user. Ask all questions in a single message, grouped logically. Examples of things to clarify:

- Scope: what is explicitly in and out of scope?
- User roles: who are the actors?
- Edge cases: error states, empty states, permission boundaries
- Dependencies: does this block or depend on other work?
- Non-functional requirements: performance, security, accessibility expectations
- Success criteria: how will this be tested or verified?

Wait for the user's answers before proceeding.

### Step 4 — Write the Specification

Write the spec as a Markdown document. Use this structure (adapt sections as appropriate):

```markdown
# Specification: <Issue Title>

**Issue:** <link or reference>
**Status:** Draft
**Author:** <leave blank or use git config user.name>
**Date:** <today's date>

---

## Overview

One paragraph describing what this feature/change does and why it exists.

## Goals

- What this achieves (outcome-focused)

## Non-Goals

- What is explicitly out of scope

## Background & Context

Any relevant background, prior decisions, or linked issues.

## Requirements

### Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-1 | ... | Must |
| FR-2 | ... | Should |

### Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-1 | ... |

## User Stories / Scenarios

**As a** [role], **I want to** [action], **so that** [outcome].

Include edge cases and error scenarios.

## Acceptance Criteria

- [ ] Criterion 1
- [ ] Criterion 2

## Open Questions

Any unresolved questions that need follow-up.
```

Present the draft to the user for review.

### Step 5 — Update the Issue (with Confirmation)

After the user reviews and approves the spec, ask:

> "Would you like me to update the issue with this specification?"

If yes:
- **GitHub**: Use `gh issue edit <number> --body "<spec content>"` or append the spec as a comment with `gh issue comment <number> --body "<spec content>"`. Prefer commenting to preserve the original issue body unless the user says to replace it.
- **JIRA**: Use `PUT /rest/api/2/issue/<issue-key>` to update the description field, or `POST /rest/api/2/issue/<issue-key>/comment` to add a comment.

Confirm success and provide a link to the updated issue.

## Notes

- Always confirm before updating the issue tracker — this is a shared system action.
- Keep the spec grounded in what was discussed; do not invent requirements.
- If the issue already has detailed acceptance criteria, incorporate them rather than replacing them.
- If the user wants to save the spec as a local file instead of (or in addition to) updating the tracker, write it to `docs/specs/<issue-slug>.md` or ask where they'd like it saved.
