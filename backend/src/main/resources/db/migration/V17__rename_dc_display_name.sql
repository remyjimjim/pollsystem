-- Rename the District of Columbia state row's display name to "Wash D.C."
-- so the State picker shows a tighter label. The USPS initial ('DC') and
-- every foreign-key reference (counties, county_zips, role_assignments,
-- etc.) key off the row's id, not the name — so the rename is a pure
-- label change with no relational impact.
UPDATE states
SET name = 'Wash D.C.'
WHERE initial = 'DC';
