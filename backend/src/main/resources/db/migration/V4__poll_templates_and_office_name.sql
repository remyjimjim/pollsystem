-- V4__poll_templates_and_office_name.sql
-- Adds Super-defined JSON templates per poll type, and the name column on
-- offices that the class diagram specifies but V1 omitted.

ALTER TABLE poll_types
    ADD COLUMN template_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE offices
    ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT '';

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
      "item": {
        "name": {"required": true},
        "affiliation": {"required": true},
        "officeName": {"required": true}
      }
    }
  }
}'::jsonb
WHERE name = 'Election';

UPDATE poll_types SET template_json = '{
  "type": "Questionnaire",
  "fields": {
    "title": {"required": true, "maxLength": 500},
    "summary": {"required": true},
    "closeDate": {"required": false, "type": "datetime"},
    "domains": {
      "required": true,
      "minItems": 1,
      "item": {"zipcode": {"required": true, "type": "zipcode"}}
    },
    "questions": {
      "required": true,
      "minItems": 1,
      "item": {"text": {"required": true, "maxLength": 1000}}
    }
  }
}'::jsonb
WHERE name = 'Questionnaire';

UPDATE poll_types SET template_json = '{
  "type": "BallotMeasure",
  "fields": {
    "title": {"required": true, "maxLength": 500},
    "summary": {"required": true},
    "electionId": {"required": true, "type": "id"},
    "effectiveDate": {"required": true, "type": "date"},
    "closeDate": {"required": false, "type": "datetime"}
  }
}'::jsonb
WHERE name = 'Referendum/Ballot Measure';
