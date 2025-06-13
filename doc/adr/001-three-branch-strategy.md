# ADR-001: Three-Branch Fork Management Strategy

## Status
**Accepted** - 2025-05-28

## Context
When maintaining a long-lived fork of an upstream repository, teams need to balance staying current with upstream changes while preserving their own modifications. Traditional forking approaches often lead to complex merge conflicts, difficulty tracking upstream changes, and challenges in maintaining a stable release branch.

The system needs to support:
- Regular synchronization with upstream repositories
- Isolation of local modifications from upstream changes
- Safe conflict resolution workflow
- Stable release management
- Clear separation of concerns between different types of changes

## Decision
Implement a three-branch strategy for fork management:

1. **`main`** - Stable production branch containing successfully integrated changes
2. **`fork_upstream`** - Tracks the upstream repository's main branch exactly
3. **`fork_integration`** - Workspace for resolving conflicts between upstream and local changes

## Rationale

### Branch Purposes
- **`main`**: Protected branch that only receives changes through PRs, ensuring stability
- **`fork_upstream`**: Clean tracking of upstream without local modifications, enabling clear diff analysis
- **`fork_integration`**: Dedicated space for conflict resolution without affecting stable branches

### Workflow Benefits
1. **Clear Change Attribution**: Easy to identify what comes from upstream vs local modifications
2. **Conflict Isolation**: Merge conflicts are resolved in a dedicated branch before affecting main
3. **Upstream Tracking**: Pure upstream branch enables accurate diff analysis and change detection
4. **Safe Integration**: Multiple review points before changes reach the stable main branch
5. **Rollback Capability**: Easy to revert problematic integrations without losing upstream sync

## Alternatives Considered

### 1. Two-Branch Strategy (fork + main)
- **Pros**: Simpler branch structure
- **Cons**: Conflicts would occur directly on main branch, no dedicated conflict resolution space
- **Decision**: Rejected due to safety concerns

### 2. Feature Branch per Upstream Sync
- **Pros**: Each sync is isolated
- **Cons**: Branch proliferation, complex tracking of multiple upstream syncs
- **Decision**: Rejected due to complexity

### 3. Direct Upstream Merge to Main
- **Pros**: Simplest possible approach
- **Cons**: No conflict isolation, high risk of breaking main branch
- **Decision**: Rejected due to lack of safety controls

## Consequences

### Positive
- **Stability**: Main branch remains stable through protected PR workflow
- **Clarity**: Clear separation between upstream changes and local modifications
- **Safety**: Multiple integration points prevent problematic changes from reaching production
- **Traceability**: Easy to track source of changes and resolve conflicts systematically
- **Flexibility**: Can handle complex upstream changes without disrupting ongoing development

### Negative
- **Complexity**: Three branches require more management overhead
- **Learning Curve**: Team needs to understand the branch strategy and workflows
- **Automation Dependency**: Requires automated workflows to manage branch synchronization effectively

## Implementation Details

### Branch Protection Rules
- `main`: Require PR reviews, status checks, and up-to-date branches
- `fork_upstream`: Allow direct pushes from automation only
- `fork_integration`: Allow direct pushes for conflict resolution

### Workflow Integration
1. **Upstream Sync**: `fork_upstream` tracks upstream automatically
2. **Change Detection**: Compare `fork_upstream` with `main` to identify new upstream changes
3. **Conflict Resolution**: Create PR from `fork_upstream` to `fork_integration` when conflicts exist
4. **Integration**: Create PR from `fork_integration` to `main` after conflict resolution
5. **Direct Integration**: Create PR from `fork_upstream` to `main` when no conflicts exist

### Automation Requirements
- Scheduled upstream synchronization to `fork_upstream`
- Automated conflict detection and PR creation
- Branch protection enforcement
- Status checks and validation workflows

## Success Criteria
- Teams can safely integrate upstream changes without breaking main branch
- Conflicts are resolved in isolated environment before affecting production
- Clear audit trail of all changes and their sources
- Reduced time to resolve upstream integration issues
- Maintained stability of main branch throughout integration process