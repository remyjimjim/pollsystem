# Moderation: listed words

BallotBox keeps a list of words that may not appear in what people type, each
with a replacement. The list is `backend/src/main/resources/moderation/bad-words.json`,
with entries like `{"bad-word": "…", "replace": "…"}`. It never reaches the
browser: pages send their text to `POST /api/moderation/check` and get back
only the words found.

**Matching:** a whole word, ignoring case. Plurals and possessives (-s, -es,
-'s) count too, so each word is listed once. Other forms ("darned") match
only if they're listed themselves. To change the list, edit the file and
deploy the backend.

## Comments (since 2026-10-25)

When an answer form is submitted, each listed word in a **public** comment
asks "Can I replace X with Y?":
- **Yes** replaces it.
- **No** keeps that comment **private**; the answers are still submitted.

The server rechecks, so a comment that still contains a listed word is
stored private.

## Everything else people type (since 2026-11-01)

The same question is asked on **save** for:

| Kind | Fields |
|---|---|
| Public text | Poll titles, summaries, questions and office names, in the poll forms and in Admin > Manage Polls edits |
| Private text (admins and supers see it) | Creator and admin request reasons, poll notes, disable reasons (a creator or a grant), IP rule notes, Manage Users messages |

What **No** does depends on the **text mode** (below).

**Not checked:**

- **Candidates' names and affiliations.** They're real people: "Chris
  Coons" trips the plural rule.
- Zipcodes, sign-in codes, searches and filters, "type DELETE"
  confirmations, poll templates.

**Ballot measure summaries** are checked but never refused, since they're
often official ballot language. No keeps them as typed, in either mode.

## The text mode: block or warn

Set by `moderation.text-mode` in `application.yml`. On Fly it comes from the
environment variable `MODERATION_TEXT_MODE` (in `backend/fly.toml` and
`backend/fly.staging.toml`, under `[env]`).

| Mode | The dialog's No button | On No | The server |
|---|---|---|---|
| `block` (current) | "No, I'll reword it" | Back to the field; the page shows "Please reword "X" first." and doesn't save | Refuses such a save with a 400: "Please reword "X" in the title" (or "in question 2", "in the reason"…) |
| `warn` | "No, keep it as typed" | Saved as typed | Accepts it |

Comments aren't affected by the mode: No always keeps them private.

The page learns the mode from each check, so switching needs **no frontend
change or deploy**, only a backend restart with the new value. How to
switch: [DEPLOYING-FLY.md → Switch the word mode](DEPLOYING-FLY.md#switch-the-word-mode-moderation_text_mode).

## Where it lives in the code

- **Server:** `moderation/BadWords.kt`:
  - `foundIn`;
  - `publishable` (comments);
  - `requireClean` (the other text; does nothing in warn mode);
  - `TextMode`.
  Each save endpoint calls `requireClean("the title" to …, …)`.
- **Page:**
  - `composables/useCommentModeration.ts` for comments;
  - `composables/useTextModeration.ts` (`reviewText`) for the rest;
  - the shared dialog is `components/ReplaceWordDialog.vue`, mounted once in
    `App.vue` for text fields.
- **Tests:** `BadWordsControllerTest`, `TextModerationTest`,
  `useCommentModeration.spec.ts`, `useTextModeration.spec.ts`.
