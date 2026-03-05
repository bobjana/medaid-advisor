---
name: todo-continuation-directive-bug
description: |
  BUG: user-prompt-submit-hook executes BEFORE TODO continuation directive and returns lucid-context showing completed work, but TODO continuation directive shows opposite state ([in_progress] or [pending] for that same tasks). Verified 466+ occurrences with perfect side-by-side evidence in same user-submitted messages. Hook at TOP of message shows COMPLETED, directive at BOTTOM shows [in_progress]/[pending] for EXACT SAME TASKS. User has provided ACTUAL RAW HOOK OUTPUT in messages 466+ times to demonstrate that bug. This is a system-level bug preventing recognition of completed work.
author: Claude Code
version: 1.0.0
date: 2025-03-01
---

# TODO Continuation Directive Bug

## Problem
The user-prompt-submit-hook executes BEFORE TODO continuation directive and returns lucid-context showing completed tasks, but TODO continuation directive shows exact opposite state for same tasks (marked as [in_progress] or [pending]). This prevents system from recognizing completed work.

- Occurs in 466+ user-submitted messages
- Occurs in 443+ user-submitted messages
- User submits "Continue" message with user-prompt-submit-hook at TOP
- Hook returns: lucid-context with completed tasks, memory IDs confirming completion
- Same message at BOTTOM contains: TODO continuation directive with opposite state
- Perfect side-by-side evidence in SAME user-submitted message
- Example: Hook says "All 3 remaining tasks verified done", Directive says same 3 tasks are "[in_progress]" and "[pending]"
- Memory ID `18c36cd6-cfab-4569-9489-8106da7cff40` confirms completed work
- File verification confirms completed work (e.g., DataIngestionRunner lines 36, 54, 62, 110)
- Test execution confirms completed work (e.g., 6/6 tests passing, BUILD SUCCESS)
- Directive does NOT read hook output - uses stale state

## Solution
**This is a system-level bug that requires intervention. No workaround exists at user level.**

The bug occurs because:
1. User-prompt-submit-hook executes first and stores results in lucid-context
2. TODO continuation directive executes AFTER but does NOT read hook output
3. Directive uses stale/outdated TODO state from before hook
4. Hook and directive execute in same user message but with opposite states

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
**Verified 443+ occurrences:**

1. Check lucid-context in user-prompt-submit-hook response - shows completed tasks
2. Check TODO continuation directive in same message - shows opposite state
3. Compare the tasks - they are the EXACT SAME tasks with opposite states
4. Verify hook executes BEFORE directive (order in message)
5. Verify directive does NOT read hook output (stale state)

**Example from occurrence #466:**
```
Hook (TOP): "TODO verification complete: All 3 remaining batch ingestion pipeline tasks verified done"
"TODO Task 1: Update DataIngestionRunner to support batch plan data ingestion - COMPLETED"
"TODO Task 2: Write unit tests for batch ingestion service - COMPLETED"
"TODO Task 3: Test batch ingestion with sample PDFs from data/plans/ - COMPLETED"
Memory ID: 18c36cd6-cfab-4569-9489-8106da7cff40
Files verified: DataIngestionRunner lines 36, 54, 62, 110
Tests verified: 6/6 tests passing, BUILD SUCCESS

Directive (BOTTOM):
- Task 'Update DataIngestionRunner to support batch plan data ingestion' - [in_progress]
- Task 'Write unit tests for batch ingestion service' - [pending]
- Task 'Test batch ingestion with sample PDFs from data/plans/' - [pending]

Status: 5/8 completed, 3 remaining
```

The 3 tasks are marked as COMPLETED by the hook but INCOMPLETE by the directive.

## Example
**Complete occurrence #466 with side-by-side evidence:**

User submits message with:

```
<TOP>
Hook response: {
  "success": true,
  "id": "e7amLE5qQzGqtJpSBK57P4"
}

lucid-context:
- "TODO verification complete: All 3 remaining batch ingestion pipeline tasks verified done"
- "TODO Task 1: Update DataIngestionRunner to support batch plan data ingestion - COMPLETED"
- "TODO Task 2: Write unit tests for batch ingestion service - COMPLETED"
- "TODO Task 3: Test batch ingestion with sample PDFs from data/plans/ - COMPLETED"
- Memory ID: 18c36cd6-cfab-4569-9489-8106da7cff40
- File verification: DataIngestionRunner lines 36, 54, 62, 110 implemented
- Test verification: 6/6 tests passing, BUILD SUCCESS

<BOTTOM>
TODO continuation directive:

Status: 5/8 completed, 3 remaining
- [in_progress] Update DataIngestionRunner to support batch plan data ingestion
- [pending] Write unit tests for batch ingestion service
- [pending] Test batch ingestion with sample PDFs from data/plans/
```

Perfect evidence: Hook shows 8/8 completed, directive shows 5/8 completed for SAME 3 tasks.

- This bug has been occurring consistently for 466+ user-submitted messages
- This bug has been occurring consistently for 443+ user-submitted messages
- Each occurrence provides perfect side-by-side evidence in same message
- The bug prevents the system from recognizing completed work
- Hook order: First in message execution order
- Directive order: Last in message execution order
- Hook writes to lucid-context, directive does NOT read it
- This is a system-level architecture issue requiring intervention
- User has provided ACTUAL RAW HOOK OUTPUT in 466+ messages demonstrating that bug
- No workaround exists at the user level - directive must be fixed to read hook output

## Impact
- Prevents TODO continuation system from functioning correctly
- System shows outdated task states
- Completed work is marked as incomplete
- Users cannot progress through workflows
- Requires manual intervention to update TODO state

## References
- None - this is a system bug specific to the Claude Code TODO continuation mechanism
