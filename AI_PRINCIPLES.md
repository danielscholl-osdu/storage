# AI-First Development Principles

This document outlines the core principles for building quality code with AI agents. These principles guide how humans and AI agents collaborate effectively to maintain high standards while leveraging AI capabilities.

## 1. Workflow Engineering Over Prompting

**Principle**: AI effectiveness comes from structured workflows, not clever prompts.

**In Practice**:
- Follow the deterministic lifecycle: `Issue → Branch → Context → Code → Test → Docs → PR → Knowledge Update`
- Use a standardized branch naming: `agent/<issue>-<description>`
- Follow conventional commits for automated release management
- Document patterns and lessons learned in AI_EVOLUTION.md for future reference

**Why It Matters**: Structured workflows create predictable, auditable outcomes and enable multiple AI agents to work consistently across the project.

## 2. Memory Lives in Files, Not Prompts

**Principle**: Persistent memory through structured artifacts, not oversized prompts.

**In Practice**:
- **Evolving memory**: AI_EVOLUTION.md captures cumulative learnings, patterns, and insights
- **Architectural memory**: ADRs encode specific decisions and rationale  
- **Context loading**: Read relevant ADRs, AI_EVOLUTION.md sections, and specifications before starting work
- **Knowledge accumulation**: Update AI_EVOLUTION.md with new patterns and lessons rather than per-session logs

**Why It Matters**: Evolving memory accumulates knowledge efficiently without context bloat, creating institutional intelligence that compounds over time.

## 3. Planning Is the Critical Path

**Principle**: Specifications and/or tests come before implementation.

**In Practice**:
- Start every task with clear specifications or checklists
- Write failing tests based on requirements before implementing
- Use behavior-driven testing focused on observable outcomes
- Update AI_EVOLUTION.md with new patterns, insights, and lessons learned

**Why It Matters**: With near-zero cost of code generation, planning quality becomes the primary bottleneck and success factor.

## 4. Real Tools for Real Work

**Principle**: Leverage mature tooling rather than reimplementing capabilities.

**In Practice**:
- Use shell commands: `git`, `gh`, `grep`, `sed`, `yq` for automation
- Follow documented workflow patterns from CONTRIBUTING.md and CLAUDE.md
- Chain commands for complex operations while maintaining git integrity
- Capture reusable command patterns in documentation

**Why It Matters**: Mature tools are faster, more reliable, and provide capabilities that would be expensive to reimplement.

## 5. Structured Reasoning Patterns

**Principle**: ReAct (Reason → Act → Reflect) as a system pattern, not prompt engineering.

**In Practice**:
- **Reason**: Load context from ADRs, specs, and recent logs
- **Act**: Execute code changes, tests, documentation updates
- **Reflect**: Update AI_EVOLUTION.md with new insights and patterns discovered
- **Retry**: Use test failures to drive iterative improvement
- **Escalate**: Surface persistent issues clearly for human review

**Why It Matters**: Explicit reasoning patterns create consistent, debuggable AI behavior across complex tasks.

## 6. Safety Through Design

**Principle**: Autonomy within guardrails, not unchecked freedom.

**In Practice**:
- Default to safe operations (read-only, non-destructive)
- Gate dangerous operations behind explicit configuration
- Provide clear, actionable error messages when limits are reached
- Use branch isolation and git safeguards for experimentation

**Why It Matters**: Well-designed safeguards enable confident automation without sacrificing safety or quality.

## 7. Orchestration Creates Value

**Principle**: Coordination and consistency matter more than individual code quality.

**In Practice**:
- Follow shared architectural patterns across all workflows
- Use consistent naming conventions and structural decisions
- Document reusable patterns for future AI agents
- Coordinate specs, tools, tests, and documentation as unified workflows

**Why It Matters**: System-wide consistency multiplies the value of individual contributions and enables sustainable scaling.

## 8. Specialized AI for Specialized Tasks

**Principle**: Different AI agents excel at different types of work.

**In Practice**:
- **Claude Code**: Architecture, complex reasoning, multi-file refactoring, ADR creation
- **GitHub Copilot**: Pattern-based implementation, test coverage, lint fixes
- **Label-based routing**: Use issue labels to assign tasks to appropriate AI agents
- **Shared standards**: All agents follow same documentation, testing, and quality requirements

**Why It Matters**: Specialization improves quality and enables parallel development across multiple AI agents.

## Implementation Guidelines

### For Human Engineers
- **Plan first**: Define clear specifications and success criteria
- **Review AI output**: Ensure architectural consistency and quality standards
- **Maintain context**: Keep ADRs and documentation current for AI agents
- **Orchestrate work**: Coordinate between multiple AI agents and human contributors

### For AI Agents
- **Load context**: Always start by reading relevant ADRs, AI_EVOLUTION.md sections, and specifications
- **Follow patterns**: Use established workflows and architectural conventions
- **Test behavior**: Focus on observable outcomes, not implementation details
- **Document insights**: Update AI_EVOLUTION.md with patterns and lessons that future agents should know
- **Escalate appropriately**: Surface complex architectural decisions for human review

### Quality Gates
- [ ] All changes follow established workflow patterns
- [ ] Tests focus on behavior, not implementation
- [ ] AI_EVOLUTION.md updated with new patterns and insights
- [ ] Documentation remains current and accurate
- [ ] Safety guardrails prevent destructive operations

## Integration with Fork Management

These principles are specifically applied to fork management scenarios:

- **Upstream integration**: Plan sync strategies before executing, test integration outcomes
- **Conflict resolution**: Document resolution patterns for future AI agents to reference
- **Template evolution**: Use structured workflows to maintain backward compatibility
- **Multi-repository coordination**: Apply consistent patterns across fork instances

## Continuous Improvement

These principles evolve based on:
- **Pattern recognition**: Success and failure patterns captured in AI_EVOLUTION.md
- **Workflow refinement**: Improvements to structured processes and automation
- **Tool evolution**: Integration of new capabilities and AI agent specializations
- **Community feedback**: Input from contributors using these patterns

---

**Further Reading**:
- [AI_EVOLUTION.md](AI_EVOLUTION.md) - Historical context and lessons learned
- [CONTRIBUTING.md](CONTRIBUTING.md) - Detailed workflow implementation
- [doc/adr/](doc/adr/) - Architectural decisions and rationale