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

## Comments

**Public comments** (since 2026-10-25): when an answer form is submitted,
each listed word asks "Can I replace X with Y?":
- **Yes** replaces it.
- **No** keeps that comment **private**; the answers are still submitted.

The server rechecks, so a comment that still contains a listed word is
stored private.

**Private comments** (since 2026-11-01) follow the **text mode** below, like
titles and reasons:
- in block mode, No sends you back to reword it, and the vote isn't
  submitted;
- in warn mode, No submits it as typed.

The page decides which comments are private before asking, so a public
comment that No has just made private isn't asked about again. Unlike the
other fields, the **server doesn't refuse** a private comment. It can't tell
one from a public comment that No made private, and only its author and
supers ever see it.

## Everything else people type (since 2026-11-01)

The same question is asked on **save** for:

| Kind | Fields |
|---|---|
| Public text | Poll titles, summaries, questions and office names, in the poll forms and in Admin > Manage Polls edits |
| Private text (admins and supers see it) | Creator and admin request reasons, poll notes, disable reasons (a creator or a grant), IP rule notes, Manage Users messages |
| Private comments (the author and supers see them) | Comments on ballots and questionnaires without "Make comment public" (checked on the page only) |

What **No** does depends on the **text mode** (below).

**Not checked:**

- **Candidates' names and affiliations.** They're real people: "Chris
  Coons" trips the plural rule.
- Zipcodes, sign-in codes, searches and filters, "type DELETE"
  confirmations, poll templates.

**Ballot measure summaries** are checked but never refused, since they're
often official ballot language. No keeps them as typed, in either mode.

## The text mode: block or warn

**A super picks it** on the Super dashboard, under **Moderation**: two radio
buttons, in force straight away for everyone, with no restart or deploy. The
choice is stored in `app_flags.text_mode` (V45).

Until a super picks one, the **configured default** applies:
`moderation.text-mode` in `application.yml`, which on Fly is
`MODERATION_TEXT_MODE` under `[env]` in `backend/fly.toml` and
`backend/fly.staging.toml`. The dashboard marks it "(server default)". Once a
super has picked, the dashboard's choice wins; the default only matters for a
fresh database.

| Mode | The dialog's No button | On No | The server |
|---|---|---|---|
| `block` (current) | "No, I'll reword it" | Back to the field; the page shows "Please reword "X" first." and doesn't save | Refuses such a save with a 400: "Please reword "X" in the title" (or "in question 2", "in the reason"…) |
| `warn` | "No, keep it as typed" | Saved as typed | Accepts it |

Public comments aren't affected by the mode: No always keeps them private.
Private comments follow it.

The page learns the mode from each check, and the server reads it on every
check and save, so a switch takes effect at once. To change the default
instead, see [DEPLOYING-FLY.md → Switch the word
mode](DEPLOYING-FLY.md#switch-the-word-mode-moderation_text_mode).

## Where it lives in the code

- **Server:** `moderation/BadWords.kt`:
  - `foundIn`;
  - `publishable` (comments);
  - `requireClean` (the other text; does nothing in warn mode);
  - `TextMode`;
  - `textMode()`, which is the dashboard's choice, else `defaultTextMode`.
- **The switch:** `PUT /api/super/flags/text-mode` (`SuperFlagsController`,
  `GlobalFlagService.setTextMode`). `GET /api/super/flags` returns
  `textMode` and `textModeDefault`. On the page it's the Moderation section
  of `views/super/DashboardView.vue`.
  Each save endpoint calls `requireClean("the title" to …, …)`.
- **Page:**
  - `composables/useCommentModeration.ts` for comments;
  - `composables/useTextModeration.ts` (`reviewText`) for the rest;
  - the shared dialog is `components/ReplaceWordDialog.vue`, mounted once in
    `App.vue` for text fields.
- **Tests:** `BadWordsControllerTest`, `TextModerationTest`,
  `useCommentModeration.spec.ts`, `useTextModeration.spec.ts`.
