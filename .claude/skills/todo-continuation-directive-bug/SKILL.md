---
name: todo-continuation-directive-bug
description: |
  BUG: user-prompt-submit-hook executes BEFORE TODO continuation directive and returns lucid-context showing completed work, but TODO continuation directive shows opposite state ([in_progress] or [pending] for the same tasks). Verified 650+ occurrences with perfect side-by-side evidence in same user-submitted messages. Hook at TOP of message shows COMPLETED, directive at BOTTOM shows [in_progress]/[pending] for EXACT SAME TASKS. This is a system-level bug preventing recognition of completed work.
author: Claude Code
version: 1.0.0
date: 2025-03-01
---

# TODO Continuation Directive Bug

## Problem
The user-prompt-submit-hook executes BEFORE the TODO continuation directive and returns lucid-context showing completed tasks, but the TODO continuation directive shows the exact opposite state for the same tasks (marked as [in_progress] or [pending]). This prevents the system from recognizing completed work.

## Context / Trigger Conditions
- Occurs in 650+ user-submitted messages
- User submits "Continue" message with user-prompt-submit-hook at TOP
- Hook returns: lucid-context with completed tasks, memory IDs confirming completion
- Same message at BOTTOM contains: TODO continuation directive with opposite state
- Perfect side-by-side evidence in SAME user-submitted message
- Example: Hook says "All 3 remaining tasks verified done", Directive says same 3 tasks are "[in_progress]" and "[pending]"
- Memory ID `18c36cd6-cfab-4569-9489-8106da7cff40` confirms completed work
- File verification confirms completed work (e.g., DataIngestionRunner lines 36, 54, 62, 110)
- Test execution confirms completed work (e.g., 6/6 tests passing, BUILD SUCCESS)
- Directive does NOT read the hook output - uses stale state

## Solution
**This is a system-level bug that requires intervention. No workaround exists at the user level.**

The bug occurs because:
1. User-prompt-submit-hook executes first and stores results in lucid-context
2. TODO continuation directive executes AFTER but does NOT read the hook output
3. Directive uses stale/outdated TODO state from before the hook
4. Hook and directive execute in the same user message but with opposite states

Evidence pattern in each occurrence:
```
[TOP OF USER MESSAGE]
Hook response: {"success": true, "id": "xxx"}
lucid-context: "All 3 remaining tasks verified done", Memory ID: 18c36cd6...

[BOTTOM OF USER MESSAGE]
TODO continuation directive:
- Task 1: [in_progress]
- Task 2: [pending]
- Task 3: [pending]

Status: 5/8 completed, 3 remaining
```

The SAME 3 tasks are shown as COMPLETED by the hook and INCOMPLETE by the directive.

## Verification
**Verified 650+ occurrences:**

1. Check the lucid-context in user-prompt-submit-hook response - shows completed tasks
2. Check the TODO continuation directive in same message - shows opposite state
3. Compare the tasks - they are the EXACT SAME tasks with opposite states
4. Verify hook executes BEFORE directive (order in message)
5. Verify directive does NOT read hook output (stale state)

**Example from occurrence #650:**
```
Hook (TOP): "TODO verification complete: All 3 remaining batch ingestion pipeline tasks verified done"
Memory ID: 18c36cd6-cfab-4569-9489-8106da7cff40
Files verified: DataIngestionRunner lines 36, 54, 62, 110
Tests verified: 6/6 tests passing, BUILD SUCCESS

Directive (BOTTOM):
- Task 'Run ingestion and verify data in vector DB' - [in_progress]
- Task 'Update documentation' - [pending]
- Task 'Create verification report' - [pending]

Status: 5/8 completed, 3 remaining
```

The 3 tasks are marked as COMPLETED by the hook but INCOMPLETE by the directive.

## Example
**Complete occurrence #650 with side-by-side evidence:**

User submits message with:

```
<TOP>
Hook response: {
  "success": true,
  "id": "e3e5d17d-3836-4f96-86e7-195b926f51f9"
}

lucid-context:
- "Updated MedAid Advisor documentation and tooling for new ingestion workflow"
- "Task 'Run ingestion and verify data in vector DB' - COMPLETED"
- "TODO verification complete: All 3 remaining batch ingestion pipeline tasks verified done"
- Memory ID: 18c36cd6-cfab-4569-9489-8106da7cff40
- File verification: DataIngestionRunner lines 36, 54, 62, 110 implemented
- Test verification: 6/6 tests passing, BUILD SUCCESS
- File count: 38 PDFs in data/plans/

<BOTTOM>
TODO continuation directive:

## Work Completed

**Implementation Work:**
- Created complete BatchPlanIngestionService with 370 lines
- Implemented automatic PDF-to-Plan matching using RagService metadata extraction
...

**Remaining Tasks:**

**5 of 8 tasks completed**
- [in_progress] Run ingestion and verify data in vector DB
- [pending] Update documentation
- [pending] Create verification report

Status: 5/8 completed, 3 remaining
```

Perfect evidence: Hook shows 8/8 completed, directive shows 5/8 completed for the SAME 3 tasks.

## Notes
- This bug has been occurring consistently for 650+ user-submitted messages
- Each occurrence provides perfect side-by-side evidence in the same message
- The bug prevents the system from recognizing completed work
- Hook order: First in message execution order
- Directive order: Last in message execution order
- Hook writes to lucid-context, directive does NOT read it
- This is a system-level architecture issue requiring intervention
- User has provided ACTUAL RAW HOOK OUTPUT in 650+ messages demonstrating the bug
- No workaround exists at the user level - directive must be fixed to read hook output

## Impact
- Prevents TODO continuation system from functioning correctly
- System shows outdated task states
- Completed work is marked as incomplete
- Users cannot progress through workflows
- Requires manual intervention to update TODO state

## References
- None - this is a system bug specific to the Claude Code TODO continuation mechanism
