# Misc notes

Small project-wide conventions and "how do I…" answers that don't fit any
one of the other docs. Append entries to the bottom; keep each entry short.

---

## Identifying Vue components in the rendered DOM

Vue compiles components down to plain HTML, so `<ZipSetter>` doesn't show
up in the elements panel by name. Two ways to find one:

**Vue DevTools** (browser extension) — canonical. The Components tab
shows the live component tree by name, so `<ZipSetter>` is identifiable
without touching the markup.

**`data-component` attribute** — for raw-DOM grepping. Each component we
care about for inspection carries a `data-component="<kebab-name>"` on
its root element. Use it from the browser console:

```js
document.querySelectorAll('[data-component="zipsetter"]')
$$('[data-component="poll-search-view"]')   // Chrome / Firefox shortcut
```

Or filter the elements panel with `[data-component="…"]` as a CSS
selector.

Components currently tagged:

| Attribute value           | File                                             |
|---------------------------|--------------------------------------------------|
| `zipsetter`               | `frontend/src/components/ZipSetter.vue`          |
| `poll-search-view`        | `frontend/src/views/PollSearchView.vue`          |
| `admin-request-view`      | `frontend/src/views/AdminRequestView.vue`        |
| `creator-request-view`    | `frontend/src/views/CreatorRequestView.vue`      |
| `questionnaire-form`      | `frontend/src/components/QuestionnaireForm.vue`  |
| `election-form`           | `frontend/src/components/ElectionForm.vue`       |
| `ballot-measure-form`     | `frontend/src/components/BallotMeasureForm.vue`  |

Convention: kebab-case, identical to the component's filename minus
`.vue` (and the trailing `-view` for view components, which keeps the
search-by-name attribute distinct from any DOM class names Vue's compiler
generates). Add the attribute to a component's outermost element when
you'd want to grep for it during a page-inspection session.

---

## Form layout: Geographic scope first

Any form that needs a zipcode selection renders `<ZipSetter>` as the
**first** fieldset, under a legend of `{{ $t('common.geoScope') }}`
("Geographic scope:"). Other fields follow.

Currently applied to:

- `views/AdminRequestView.vue`
- `views/CreatorRequestView.vue`
- `components/QuestionnaireForm.vue`

`common.geoScope` is the single shared i18n key — don't reintroduce
per-namespace variants like `creatorRequest.geoScope` or
`adminRequest.zipcodesLegend` (collapsed into `common.geoScope` on
2026-06-20). If a form's geographic field needs context-specific extra
copy, put that copy in a helper `<p>` below the legend rather than
forking the legend wording.
