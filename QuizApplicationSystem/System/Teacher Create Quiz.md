# Teacher Create Quiz

> Status: Planned JavaFX feature specification. Named classes and reuse points below are design targets, not verified existing code.

The teacher sidebar opens a quiz builder using the existing QuizEditor and quiz validation models. Ownership is automatically assigned to the signed-in teacher. Only current non-archived assigned subjects appear.

Enter title, optional description, subject, and a 1-1440 minute time limit. Add or remove four-choice questions with a correct answer and positive points. Save Draft accepts zero questions; Publish requires at least one complete question. Both actions validate every entered question. After a successful save, the form clears and displays the saved quiz ID; Refresh subjects starts another quiz. Navigation preserves an unfinished form. Refresh subjects preserves entered content and clears a subject selection if it is no longer available.

Database work runs asynchronously. Controls are disabled during loading/saving. Persistence verifies active teacher access and locks the current assignment while writing the quiz and ordered questions in one transaction. Administrator editing remains available through its existing authorization path. No schema changes are required.

## Planned verification

No implementation or test suite for this feature is present in this workspace.
Verify the behavior above with Service/DAO tests and JavaFX UI checks, including
role isolation, session changes, loading/error states, and responsive layout.
