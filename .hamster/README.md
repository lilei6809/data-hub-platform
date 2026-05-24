# Hamster Project Context

This directory contains project context synced from Hamster Studio.

## Structure

```
.hamster/[account-slug]/
├── briefs/
│   └── [brief-slug]/
│       ├── brief.md              # Brief content
│       └── tasks/
│           ├── HAM-001-task-title.md
│           └── HAM-002-subtask-title.md
├── blueprints/
│   └── [slug].md
└── methods/
    └── [slug].md

## Syncing

Use the Hamster CLI to keep this directory in sync:

```bash
hamster sync              # One-time sync
hamster sync --watch      # Continuous real-time sync
hamster status            # Check sync status
```

## Files

- **.state.json**: Sync metadata (do not edit manually)
- **README.md**: This file

## Claude Integration

The Hamster CLI automatically generates a Claude skill at:
`.claude/skills/hamster-project-context/SKILL.md`

This teaches Claude Code how to use the synced files for context-aware assistance.
