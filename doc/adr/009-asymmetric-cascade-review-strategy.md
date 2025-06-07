# ADR-009: Asymmetric Cascade Review Strategy

## Status
Accepted

## Context
The cascade workflow moves upstream changes through a three-branch hierarchy:
1. `fork_upstream` → `fork_integration` 
2. `fork_integration` → `main`

We needed to balance automation efficiency with safety, ensuring that upstream changes are properly vetted before reaching production while minimizing manual intervention where safe.

Key considerations:
- Upstream changes are external and potentially breaking
- Integration branch serves as a testing ground
- Main branch is production and must remain stable
- Manual reviews add latency to the cascade pipeline
- Conflict resolution always requires human intervention

## Decision
We will implement an asymmetric review strategy for cascade PRs:

1. **Fork_upstream → Fork_integration**: Always requires manual review
   - This is the first introduction of external changes
   - Conflicts are most likely to occur here
   - Human judgment needed to assess upstream impact

2. **Fork_integration → Main**: Eligible for auto-merge when ALL conditions are met:
   - No conflicts detected
   - All CI checks pass
   - Diff size < 1000 lines
   - No breaking changes (detected via commit messages)
   - Changes already validated in integration branch

## Consequences

### Positive
- **Safety First**: External changes get human review at entry point
- **Efficiency**: Clean, tested changes flow automatically to production
- **Clear Boundaries**: Integration branch serves its purpose as a validation gate
- **Reduced Latency**: Second phase can complete without waiting for reviewers
- **Risk Mitigation**: Large or breaking changes always get manual review

### Negative
- **Asymmetry Complexity**: Different rules for different stages may confuse
- **First-Stage Bottleneck**: All upstream changes need manual review
- **Auto-merge Risk**: Even with safeguards, automated merges carry inherent risk

### Neutral
- **Monitoring Required**: Need to track auto-merge success rates
- **Tunable Parameters**: Diff size threshold can be adjusted based on experience
- **Override Capability**: Can disable auto-merge via environment variables

## Implementation Details

### Phase 1 (Always Manual)
```yaml
# Create PR without auto-merge
gh pr create \
  --title "Integrate upstream changes" \
  --label "upstream-sync,cascade-active"
# No auto-merge command
```

### Phase 2 (Conditionally Automatic)
```yaml
if [[ "$DIFF_LINES" -lt 1000 ]] && [[ "$BREAKING_CHANGES" == "false" ]]; then
  gh pr merge --auto --squash --delete-branch
  gh pr edit --add-label "auto-merge-enabled"
else
  gh pr edit --add-label "manual-review-required"
fi
```

## Alternatives Considered

1. **Fully Manual**: Require review at both stages
   - Rejected: Too slow, defeats automation purpose

2. **Fully Automated**: Auto-merge at both stages when clean
   - Rejected: Too risky for external changes

3. **Reversed Asymmetry**: Auto-merge first stage, manual second
   - Rejected: Backwards from a safety perspective

## Related
- [ADR-001: Three-Branch Fork Management Strategy](001-three-branch-strategy.md)
- [ADR-005: Automated Conflict Management Strategy](005-conflict-management.md)
- [Cascade Workflow Specification](../cascade-workflow.md)