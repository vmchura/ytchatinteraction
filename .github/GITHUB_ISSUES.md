# GitHub Issue Creation Guide

This document provides a quick reference for creating GitHub issues using the `gh` CLI tool.

## Prerequisites

- `gh` CLI must be installed and authenticated
- Must be in a git repository with a GitHub remote
- User must have write access to the repository

## Basic Command Structure

```bash
gh issue create --title "Issue Title" --body "Issue description"
```

## Common Options

| Option | Description | Example |
|--------|-------------|---------|
| `--title` or `-t` | Issue title (required) | `--title "Bug: Login fails"` |
| `--body` or `-b` | Issue description | `--body "Description here"` |
| `--label` or `-l` | Add labels | `-l bug -l high-priority` |
| `--assignee` or `-a` | Assign users | `-a username` |
| `--milestone` or `-m` | Set milestone | `-m "v1.0"` |
| `--project` or `-p` | Add to project | `-p "Backlog"` |

## Examples

### Simple Issue
```bash
gh issue create --title "Fix typo in README" --body "There's a typo in the installation section"
```

### Issue with Labels and Assignee
```bash
gh issue create \
  --title "Bug: Tournament creation fails" \
  --body "Users cannot create tournaments when..." \
  --label bug --label high-priority \
  --assignee vmchura
```

### Multi-line Body (using heredoc)
```bash
gh issue create --title "Feature: Add user notifications" --body "$(cat <<'EOF'
## Description
Add a notification system for users when:
- Tournament starts
- Match is scheduled
- Results are updated

## Acceptance Criteria
- [ ] Database schema updated
- [ ] API endpoints created
- [ ] Frontend notifications UI
- [ ] Email notifications (optional)
EOF
)"
```

### Using a Template File
```bash
gh issue create --title "Bug Report" --body "$(cat issue_template.md)"
```

## Best Practices

1. **Clear Titles**: Use prefixes like `Bug:`, `Feature:`, `Refactor:`, `Docs:`
2. **Detailed Descriptions**: Include:
   - What the issue is
   - Steps to reproduce (for bugs)
   - Expected vs actual behavior
   - Screenshots or code snippets
3. **Appropriate Labels**: Helps with categorization and filtering
4. **Assign Early**: Assign to relevant team members

## Viewing and Managing Issues

```bash
# List all issues
gh issue list

# List open issues
gh issue list --state open

# View specific issue
gh issue view 16

# Close an issue
gh issue close 16

# Reopen an issue
gh issue reopen 16

# Add comment
gh issue comment 16 --body "Update on this issue..."
```

## Integration with Lazygit

When using lazygit:
1. Press `:` to open command mode
2. Run `!gh issue create --title "..." --body "..."`
3. Or exit lazygit and run gh commands directly in terminal

## Tips

- Use `--web` flag to open the issue creation in browser
- Use `--recover` to recover from failed issue creation
- Combine with shell scripts for batch issue creation
- Use markdown formatting in the body for better readability

## Quick Reference Card

```bash
# Most common pattern
ghtitle="Type: Short description"
ghbody="Detailed description here"
gh issue create -t "$ghtitle" -b "$ghbody"

# With labels
ghtitle="Bug: Something broke"
ghbody="Steps to reproduce..."
ghlabels="bug,critical"
gh issue create -t "$ghtitle" -b "$ghbody" -l "$ghlabels"
```
