# Pollsystem manual

The help library for the people who use Pollsystem. These files are the single
source: the app renders them at `/help`, so edit them here and both stay in
sync.

## Layout

One folder per audience, one file per process:

| Folder      | Who sees it in the app                         |
| ----------- | ---------------------------------------------- |
| `viewer/`   | everyone, signed in or not                     |
| `user/`     | signed-in members (USER and above)             |
| `creator/`  | creators and above                             |
| `admin/`    | admins and above                               |
| `super/`    | supers                                         |

Hiding a folder from lower access levels is a convenience, not a secret: every
page ships with the app. Never put anything confidential in the manual.

This README isn't shown in the app.

## Writing a page

Each page starts with optional front matter:

```markdown
---
title: Finding polls
summary: Search by title, state, county or zipcode.
order: 1
---
```

- `title`: the page's name in the help index (defaults to the first `# Heading`).
- `summary`: one line under the title in the index.
- `order`: position within its folder (lower first).

Link to other pages with relative paths, e.g. `[Becoming a
creator](../user/becoming-a-creator.md)`: they work on GitHub and become `/help`
links in the app. The file name (without `.md`) is the page's URL:
`viewer/finding-polls.md` → `/help/viewer/finding-polls`.

Pages are written in English first. Other languages show the English page with
a note until they're translated (see `todo.md`).

Short hover/click help next to a field doesn't belong here: it goes in the
app's language files (`frontend/src/i18n/*.json`) under `help.<view>.<element>`.

## Pages

- [x] `viewer/finding-polls.md`: finding polls by title, state, county or zipcode
- [x] `viewer/viewing-results.md`: reading results, the within/outside split, the privacy threshold
- [x] `viewer/signing-up.md`: registering, membership, signing in with a magic link (in `viewer/`: signed-out visitors need it)
- [x] `user/answering-polls.md`: voting, changing your answers
- [x] `user/becoming-a-creator.md`: requesting creator access
- [x] `creator/drafts-and-publishing.md`: dashboard, drafts, publishing checks, closing, revising, archiving
- [x] `creator/creating-a-questionnaire.md`, `creating-an-election.md`, `creating-a-ballot-measure.md`
- [ ] `creator/*`: reports
- [ ] `admin/*`: approving creators, managing creators, managing polls; reports
- [ ] `super/*`: admin requests, users, IP management, poll-type templates, the submissions kill switch; reports
