---
title: Poll templates
summary: Each poll type's JSON template, including how voters choose in elections.
order: 5
---

# Poll templates

Every poll type (Election, Questionnaire, Referendum/Ballot Measure) has a
template, written in JSON, that describes its fields.

**Poll Templates** shows each one. Click **Edit**, change the JSON, and click
**Save**. If the JSON isn't valid, you'll see "Invalid JSON" and nothing is
saved. **Cancel** throws your changes away.

## How voters choose in elections

The part of the Election template that matters most today is the candidates'
**widget**, which sets how voters choose:

| `widget`             | Voters…                                        |
| -------------------- | ---------------------------------------------- |
| `selectOneRadio`     | choose one candidate per office (the default)  |
| `selectOneList`      | choose one per office from a dropdown          |
| `selectOneCheckbox`  | tick one per office                            |
| `selectManyCheckbox` | tick any number of candidates per office       |

**This applies straight away to every election, including ones already
running.** Change it with care, and not while an election is under way. People
who have already voted keep the votes they cast.

Most other parts of the templates are descriptive for now: the app checks polls
with its own rules.
