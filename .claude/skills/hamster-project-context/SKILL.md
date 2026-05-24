---
name: hamster-project-context
description: Use when creating new briefs, blueprints, or methods, or when using the Hamster CLI to manage project context. Contains synced briefs, tasks, blueprints, and methods for this project.
---

# Hamster Project Context

## Overview

This skill provides access to synchronized project context from Hamster Studio.

**Synced Resources:**
- **Briefs**: Project requirements and product definitions
- **Tasks**: Implementation tasks with status and dependencies
- **Blueprints**: Architecture decisions and patterns (read-only)
- **Methods**: Team-specific conventions and best practices

**Last Synced:** 2026-05-23 06:24:29
**Account:** lei li's Team

## Statistics

- Briefs: 2
- Tasks: 139
- Methods: 0
- Blueprints: 0

## Quick Reference

| Task | How to Do It | File Location |
|------|--------------|---------------|
| Find a task by ID | Read task file | `.hamster/{account}/briefs/{brief-slug}/tasks/{DISPLAY-ID}-{task-slug}.md` |
| Read task notes | Read markdown file | `.hamster/{account}/briefs/{brief-slug}/tasks/{DISPLAY-ID}-{task-slug}.md` |
| Browse tasks for a brief | List tasks directory | `.hamster/{account}/briefs/{brief-slug}/tasks/` |
| Understand project scope | Read brief | `.hamster/{account}/briefs/{brief-slug}/brief.md` |
| Check team conventions | Read methods | `.hamster/{account}/methods/*.md` |
| Review architecture | Read blueprints | `.hamster/{account}/blueprints/*.md` |

## File Structure

### Directory Layout

All synced files are organized under `.hamster/{account-slug}/` with the following structure:

```
.hamster/{account-slug}/
├── briefs/
│   └── {brief-slug}/
│       ├── brief.md              # Brief content
│       └── tasks/
│           ├── HAM-001-task-title.md
│           └── HAM-002-subtask-title.md
├── blueprints/
│   └── {slug}.md
├── methods/
│   └── {slug}.md
└── .state.json                    # Sync metadata (don't edit)
```

### Briefs (`.hamster/{account-slug}/briefs/{brief-slug}/`)

Each brief is a directory containing the brief content and its associated tasks:

- `brief.md` - Brief content in markdown
- `tasks/` - Tasks belonging to this brief

**Frontmatter Format:**
```yaml
---
id: \"<document_id>\"
entity_type: \"brief\"
entity_id: \"<entity_id>\"
title: \"<title>\"
status: \"<status>\"
priority: \"<priority>\"
updated_at: \"<timestamp>\"
synced_at: \"<timestamp>\"
---
```

Example:
```bash
# Read a brief
cat .hamster/{account-slug}/briefs/user-authentication/brief.md

# List tasks for a brief
ls .hamster/{account-slug}/briefs/user-authentication/tasks/
```

### Tasks (`.hamster/{account-slug}/briefs/{brief-slug}/tasks/`)

Tasks are nested under their parent brief directory as individual markdown files:

- `{DISPLAY-ID}-{task-slug}.md` - Task notes (e.g., `HAM-123-implement-auth.md`)

**Frontmatter Format:**
```yaml
---
id: \"<document_id>\"
entity_type: \"task\"
entity_id: \"<entity_id>\"
title: \"<title>\"
status: \"<status>\"
priority: \"<priority>\"
display_id: \"HAM-123\"            # Task display ID
parent_task_id: \"<uuid>\"          # Parent task reference (if subtask)
brief_id: \"<uuid>\"               # Originating brief reference
updated_at: \"<timestamp>\"
synced_at: \"<timestamp>\"
---
```

Task files also include rich metadata sections extracted from the task's metadata column,
such as **Instructions**, **Details**, **Acceptance Criteria**, and contextual key-value fields.

Example:
```bash
# Read a specific task
cat .hamster/{account-slug}/briefs/user-authentication/tasks/HAM-123-implement-auth.md

# Find all tasks for a brief
ls .hamster/{account-slug}/briefs/user-authentication/tasks/
```

### Blueprints (`.hamster/{account-slug}/blueprints/`)

Read-only architecture documents synced from the team.

Example:
```bash
cat .hamster/{account-slug}/blueprints/api-patterns.md
```

### Methods (`.hamster/{account-slug}/methods/`)

Team-specific conventions and best practices.

Example:
```bash
cat .hamster/{account-slug}/methods/code-review-process.md
```

## YAML Frontmatter Format

All synced files include YAML frontmatter with metadata:

| Field | Type | Description |
|-------|------|-------------|
| id | string | Unique document ID (UUID) |
| entity_type | string | Type of entity (brief, task, method, blueprint) |
| entity_id | string | Entity-specific ID (e.g., task display ID) |
| title | string | Human-readable title |
| status | string | Current status (e.g., todo, in_progress, done) |
| priority | string | Priority level (e.g., low, medium, high, urgent) |
| display_id | string | Task display ID (task files, e.g., HAM-123) |
| parent_task_id | string | Parent task UUID (task files, if subtask) |
| brief_id | string | Originating brief UUID (task files) |
| updated_at | string | Last update timestamp (ISO 8601) |
| synced_at | string | Last sync timestamp (ISO 8601) |

**Example:**
```yaml
---
id: \"550e8400-e29b-41d4-a716-446655440000\"
entity_type: \"task\"
entity_id: \"HAM-123\"
title: \"Implement user authentication\"
status: \"in_progress\"
priority: \"high\"
display_id: \"HAM-123\"
parent_task_id: \"<parent-uuid>\"
brief_id: \"<brief-uuid>\"
updated_at: \"2026-02-27T10:30:00Z\"
synced_at: \"2026-02-27T10:35:00Z\"
---
```

## When to Use This Skill

- **Starting a task**: Read task notes to understand requirements
- **Understanding context**: Read associated brief for project scope
- **Following conventions**: Check methods for team practices
- **Learning architecture**: Read blueprints for system design

## IMPORTANT: Can't Find a Brief or Task?

If you cannot find a brief, task, blueprint, or method on the first look — **do NOT guess or assume it doesn't exist**. The local files may be stale. Instead:

1. Run `hamster sync` to pull the latest data from Hamster Studio
2. Then search again in the refreshed `.hamster/` directory

This ensures you are working with up-to-date project context before making any decisions.

## Sync Commands

To update local context:
```bash
hamster sync                          # Sync all briefs, tasks, methods, blueprints
hamster sync --brief-id <uuid>        # Sync a single brief and its tasks only
hamster sync --watch                  # Continuous real-time sync
hamster sync --force                  # Force full re-sync ignoring cache
hamster status                        # Check sync status
```

## Team Selection

```bash
hamster init --account-id <uuid>      # Initialize with a specific team (non-interactive)
hamster team list                     # List available teams
hamster team switch                   # Switch team interactively
hamster team switch --account-id <uuid>  # Switch team non-interactively
```

## Key Files

| File | Purpose |
|------|---------|
| `.hamster/{account-slug}/briefs/*/brief.md` | Project requirements and goals |
| `.hamster/{account-slug}/briefs/*/tasks/*.md` | Task implementation notes |
| `.hamster/{account-slug}/methods/*.md` | Team conventions and processes |
| `.hamster/{account-slug}/blueprints/*.md` | Architecture decisions |
| `.hamster/{account-slug}/.state.json` | Sync metadata (don't edit manually) |

## Management Commands

### Task Workflow

When starting work on a task, assign it to yourself and move it to in_progress:
```bash
hamster task assign HAM-123
hamster task status HAM-123 in_progress
```

When the task is complete, mark it as done:
```bash
hamster task status HAM-123 done
```

### Brief Status
Update a brief's status:
```bash
hamster brief status <id-or-slug> <status>
```
Valid statuses: draft, refining, aligned, delivering, delivered, done, archived

### Task Status
Update a task's status:
```bash
hamster task status <display-id> <status>
```
Valid statuses: todo, in_progress, done

### Task Assign
Assign a task to yourself:
```bash
hamster task assign <display-id>
```

## Creating Resources

You can create briefs, blueprints, and methods directly from the CLI.
All create commands require authentication (`hamster auth login`).

**IMPORTANT:** Always provide `--content` when creating resources. Content is converted
to rich-text (YJS/ProseMirror) and appears in the Studio editor immediately. Resources
created without content will be empty documents that need manual editing in the UI.

### Create a Brief

```bash
hamster brief create --title "My Brief" --content "## Context\n..."
hamster brief create --title "My Brief" --description "Short summary" --content "## Context\n..."
echo "## From stdin" | hamster brief create --title "Piped Brief" --content -
```

**Brief content format:** DO NOT include a top-level heading (# Title) — the title is rendered separately.
Use standard markdown: ## for sections, **bold**, *italic*, - for bullets, 1. for numbered lists.

Follow this structure:
- A short summary paragraph (no heading) describing the brief
- `## Context` — project context, relevant files/areas, background
- `## Goals` — what the brief aims to achieve
- `## Phases / Approach` — high-level phases or steps
- `## Scope` — what is in and out of scope
- `## Next Steps` — immediate actions or decisions needed

Keep it high-level and concise (3-5 sections). Focus on PROJECT PLANNING, not detailed implementation.

### Create a Blueprint

```bash
hamster blueprint create --title "My Blueprint" --content "## Overview\n..."
hamster blueprint create --title "Child Blueprint" --parent <parent-doc-uuid> --content "..."
```

**Blueprint content format:** DO NOT include a top-level heading (# Title) — the title is rendered separately.
Use standard markdown: ## for sections, **bold**, *italic*, - for bullets, 1. for numbered lists.

Design the section structure to fit the content — there is no fixed template.
Each section should have a clear purpose. Include concrete details, not generic placeholders.
Keep the document structured, comprehensive, and actionable. Blueprints cover any aspect
of a company — strategy, product, market, operations, financials, culture, and more.

### Create a Method

```bash
hamster method create --title "My Method" --content "## Overview\n..."
hamster method create --title "Child Method" --parent <parent-doc-uuid> --content "..."
```

**Method content format:** DO NOT include a top-level heading (# Title) — the title is rendered separately.
Use standard markdown: ## for sections, **bold**, *italic*, - for bullets, 1. for numbered lists.

Methods document team-specific conventions and best practices. Structure sections around
the process or convention being described. Include concrete examples, dos/don'ts, and
decision criteria where applicable.

**Flags (all create commands):**

| Flag | Description |
|------|-------------|
| `--title` | Resource title (required) |
| `--description` | Optional short description |
| `--content` | Markdown content — **always provide this** (use `-` for stdin) |
| `--parent` | Parent document UUID (blueprint/method only) |

## Skills Management

Hamster syncs agent skills from your workspace into `.agents/skills/`. Skills are shared
across AI coding agents (Claude Code, Cursor, Codex, etc.) and provide reusable patterns,
conventions, and domain knowledge.

### Sync Skills

```bash
hamster skills sync                  # Bidirectional: pull updates + push local changes
hamster skills sync --pull-only      # Pull only (skip push)
hamster skills sync --push-only      # Push only (skip pull)
hamster skills agents                # Configure which IDE/agent directories get symlinks
```

The sync flow:
1. **Detects local modifications** — compares on-disk content against stored fingerprints
2. **Pulls remote updates** — fetches new, updated, or removed skills from Hamster (unless `--push-only`)
3. **Pushes local changes** — sends modified skills back to Hamster (unless `--pull-only`)
4. **Agent configuration** — select which IDE agent directories receive symlinks

### Pushing Local Changes

When you (or an AI agent) edit a skill file in `.agents/skills/`, the next
`hamster skills sync` automatically detects the change and offers to push it:

```
📝 Detected local changes (2 skills modified locally):
  • hamster-react (v1.2.0) — SKILL.md modified
  • hamster-database (v1.0.0) — 1 asset changed: references/schema-guide.md

Push 2 locally modified skill(s) to Hamster
  [x] hamster-react (v1.2.0)  [push]
  [x] hamster-database (v1.0.0)  [1 asset(s) changed]  [push]
```

**Conflict handling**: If a skill was also updated remotely since your last pull,
you will be prompted to choose: `[K]eep local` (force push), `[U]se remote` (pull instead), or `[S]kip`.

**Non-interactive mode**: Automatically pulls all updates and pushes all modified skills.
Conflicts are skipped (never auto-force-pushed) to prevent data loss.

### Skills Directory Structure

```
.agents/skills/
├── {skill-name}/
│   ├── SKILL.md                    # Main skill content
│   └── references/                 # Optional asset files
│       └── *.md
└── .hamster-sync-state.json        # Sync metadata (don't edit manually)
```

Skills are stored in `.agents/skills/` (the universal location). Symlinks are
created in agent-specific directories (e.g. `.claude/skills/`, `.cursor/skills/`)
for agents that don't read from `.agents/skills/` directly.

## Generated

This skill was auto-generated by Hamster CLI on 2026-05-23 06:24:29 UTC