-- V12__election_template_candidates_widget.sql
-- Adds rendering hints to the Election poll-type template so the voter UI
-- knows how to display candidates (radio, dropdown, checkbox group, etc.)
-- and that the list should be grouped by officeName before rendering.

UPDATE poll_types SET template_json = '{
  "type": "Election",
  "fields": {
    "title": {"required": true, "maxLength": 500},
    "date": {"required": true, "type": "date"},
    "zipcode": {"required": true, "type": "zipcode"},
    "closeDate": {"required": false, "type": "datetime"},
    "candidates": {
      "required": true,
      "minItems": 1,
      "widget": "selectOneRadio",
      "groupBy": "officeName",
      "item": {
        "name": {"required": true},
        "affiliation": {"required": true},
        "officeName": {"required": true}
      }
    }
  }
}'::jsonb
WHERE name = 'Election';
