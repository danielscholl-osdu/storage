# ADR-019: Cascade Monitor Pattern

## Status
**Accepted** - 2025-06-20

## Context

The cascade workflow needs to be triggered when upstream changes are merged into the `fork_upstream` branch. However, GitHub Actions has several limitations that complicate direct triggering:

1. **Token Limitations**: Manual PR merges via GitHub UI use `GITHUB_TOKEN` which cannot trigger other workflows
2. **Event Filtering**: Direct `push` triggers on `fork_upstream` would fire on every push, not just sync merges
3. **Race Conditions**: Simultaneous triggers could cause cascade operations to conflict
4. **Error Handling**: Failed triggers need robust error handling and fallback mechanisms

The original approach used both `push` and `pull_request` triggers directly in `cascade.yml`, but this created:
- Unwanted triggers on non-sync pushes
- Complexity in the main cascade workflow with trigger-detection logic
- No error handling for trigger failures
- Difficult debugging when triggers didn't fire as expected

## Decision

Implement a **Cascade Monitor Pattern** that separates trigger detection from cascade execution:

1. **cascade-monitor.yml**: Dedicated workflow that detects when sync PRs are merged and triggers cascade
2. **cascade.yml**: Simplified to only run on `workflow_dispatch` (manual or programmatic triggers)
3. **Event-driven Architecture**: Monitor listens for specific events and triggers the appropriate workflow
4. **Error Handling**: Monitor includes fallback mechanisms and failure notifications

## Rationale

### Separation of Concerns
- **Monitor Responsibility**: Detect sync events and trigger cascades
- **Cascade Responsibility**: Execute the integration process
- **Clean Interfaces**: Simple trigger mechanism via `gh workflow run`

### Improved Reliability
- **Explicit Trigger Logic**: Monitor only fires on PR merges with `upstream-sync` labels
- **Error Handling**: Failed triggers create notification issues for manual intervention
- **Fallback Safety**: Manual `workflow_dispatch` always available as backup

### Better Observability
- **Clear Audit Trail**: Monitor logs show when and why cascades are triggered
- **Failure Visibility**: Failed triggers create trackable issues
- **Health Monitoring**: Monitor can check cascade pipeline health

### Reduced Complexity
- **Simplified Cascade**: No complex trigger detection logic in main workflow
- **Single Responsibility**: Each workflow has one clear purpose
- **Easier Testing**: Can test trigger detection separately from cascade execution

## Alternatives Considered

### 1. Direct Push Triggers
```yaml
# In cascade.yml
on:
  push:
    branches: [fork_upstream]
```
**Pros**: Simple, immediate triggering
**Cons**: Fires on all pushes, not just sync merges; no way to filter by intent
**Decision**: Rejected due to unwanted triggers

### 2. Combined PR and Push Triggers
```yaml
# In cascade.yml (original approach)
on:
  push:
    branches: [fork_upstream, fork_integration]
  pull_request:
    types: [closed]
    branches: [fork_upstream, fork_integration]
```
**Pros**: Handles various trigger scenarios
**Cons**: Complex conditional logic; hard to debug; no error handling
**Decision**: Rejected due to complexity and reliability issues

### 3. External Webhook System
**Pros**: Maximum flexibility, external control
**Cons**: Additional infrastructure; more complex setup; maintenance overhead
**Decision**: Rejected due to complexity for minimal benefit

### 4. Scheduled Polling
```yaml
on:
  schedule:
    - cron: '*/5 * * * *'  # Every 5 minutes
```
**Pros**: Guaranteed to catch changes eventually
**Cons**: Up to 5-minute delay; inefficient; doesn't scale well
**Decision**: Rejected as primary approach (kept as backup in monitor)

## Implementation Details

### Monitor Workflow Structure
```yaml
name: Cascade Monitor

on:
  pull_request:
    types: [closed]
    branches:
      - fork_upstream
  schedule:
    - cron: '0 */6 * * *'  # Backup health monitoring
  workflow_dispatch:

jobs:
  trigger-cascade-on-upstream-merge:
    if: >
      github.event_name == 'pull_request' &&
      github.event.pull_request.merged == true &&
      github.event.pull_request.base.ref == 'fork_upstream' &&
      (contains(github.event.pull_request.labels.*.name, 'upstream-sync') ||
       contains(github.event.pull_request.labels.*.name, 'sync'))
```

### Trigger Mechanism
```bash
# Trigger cascade workflow with error handling
if gh workflow run "Cascade Integration" --repo ${{ github.repository }}; then
  echo "✅ Cascade workflow triggered successfully"
else
  echo "❌ Failed to trigger cascade workflow"
  
  # Create failure notification issue
  gh issue create \
    --title "🚨 Failed to trigger cascade workflow - $(date +%Y-%m-%d)" \
    --body "Failed to automatically trigger cascade after PR merge..." \
    --label "cascade-trigger-failed,human-required,high-priority"
  
  exit 1
fi
```

### Health Monitoring Integration
The monitor also includes periodic health checks:
- **Stale Conflict Detection**: Find conflicts older than 48 hours
- **Pipeline Health Reports**: Overall cascade pipeline status
- **Escalation Management**: Automatic escalation of long-running issues

## Consequences

### Positive
- **Reliability**: Explicit trigger conditions reduce false triggers
- **Debuggability**: Clear separation makes troubleshooting easier
- **Error Handling**: Failed triggers are visible and actionable
- **Maintainability**: Each workflow has a single, clear responsibility
- **Extensibility**: Monitor can be enhanced with additional trigger logic
- **Observability**: Better logging and issue tracking for trigger events

### Negative
- **Additional Complexity**: Two workflows instead of one
- **Potential Lag**: Small delay between PR merge and cascade trigger
- **Dependency**: Cascade workflow depends on monitor working correctly
- **Learning Curve**: Team needs to understand the two-workflow pattern

### Neutral
- **File Count**: Adds one additional workflow file
- **Maintenance**: Two simpler workflows vs. one complex workflow
- **Testing**: Need to test both trigger detection and cascade execution

## Integration Points

### With Sync Workflow
- Sync workflow creates PRs with `upstream-sync` label
- Monitor detects when these PRs are merged
- Monitor triggers cascade to process the changes

### With Cascade Workflow
- Cascade simplified to only handle `workflow_dispatch` events
- Monitor passes context via PR comments and tracking issues
- Error states handled by both workflows appropriately

### With Label Management (ADR-008)
- Uses predefined labels: `upstream-sync`, `cascade-trigger-failed`, `human-required`
- Leverages existing label-based notification system
- Maintains consistency with other workflow patterns

## Monitoring and Alerting

### Success Metrics
- **Trigger Success Rate**: % of sync merges that successfully trigger cascades
- **Trigger Latency**: Time between PR merge and cascade start
- **Error Recovery**: Time to resolve trigger failures

### Failure Modes
1. **Monitor Workflow Failure**: Creates issue for investigation
2. **Cascade Trigger Failure**: Creates notification issue with manual steps
3. **Network/API Failures**: Retries with exponential backoff

### Health Checks
- **Daily Pipeline Status**: Monitor generates health reports
- **Stale Issue Detection**: Automatically escalates old problems
- **Cascade Pipeline Monitoring**: Overall system health visibility

## Future Enhancements

### Planned Improvements
1. **Batch Triggering**: Group multiple rapid changes into single cascade
2. **Priority Queuing**: Handle urgent vs. routine upstream changes differently
3. **Smart Scheduling**: Avoid triggers during maintenance windows
4. **Cross-Repository Coordination**: Coordinate cascades across multiple forks

### Extensibility Points
- **Custom Trigger Logic**: Easy to add new trigger conditions
- **External Integrations**: Webhook support for external systems
- **Advanced Error Handling**: Sophisticated retry and recovery strategies
- **Metrics Collection**: Detailed analytics on trigger patterns

## Related Decisions

- [ADR-001: Three-Branch Fork Management Strategy](001-three-branch-strategy.md) - Defines the cascade target branches
- [ADR-005: Automated Conflict Management Strategy](005-conflict-management.md) - Conflict handling within cascades
- [ADR-008: Centralized Label Management Strategy](008-centralized-label-management.md) - Label-based state management
- [ADR-009: Asymmetric Cascade Review Strategy](009-asymmetric-cascade-review-strategy.md) - Review requirements for cascades

## Success Criteria

- 100% of valid sync PR merges trigger cascade workflows
- Failed triggers are detected and resolved within 1 hour
- Monitor workflow success rate > 99.5%
- Zero false positive triggers (non-sync changes triggering cascades)
- Clear audit trail for all trigger decisions
- Escalation process handles 100% of stuck cascades within SLA