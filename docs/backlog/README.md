# Continuous app improvement

The review and implementation cycle keeps two focused queues:

- [Backend](backend.md): protocol adapters, data handling, and connection state.
- [Frontend](frontend.md): useful features, navigation, and everyday interactions.
- [Completed cycles](cycles.md): shipped batches and their verification.

Each review pass adds a small number of evidence-backed, deduplicated items.
Stable IDs preserve the history. Ready items contain a specific user problem,
code pointers, an implementation outline, and the smallest useful verification.

The implementation owner takes a coherent batch, marks it in progress, fixes it,
runs focused checks, and records the outcome before committing and pushing.
Completed items stay in their queue as history. Reviewers move to different
areas while the current batch is implemented; they do not edit product code.

Start subsequent cycles from the merged production baseline. Preserve unfinished
work and unrelated artifacts before synchronizing the checkout. Follow repository
merge requirements; do not change protection rules to speed up the cycle.
