//Got it — so one RoleAssignment row per zipcode per user per role. That keeps the table 
//normalized and queries dead simple. 
//"Find all Admins covering zipcode 07094" is just:
SELECT * FROM RoleAssignment 
WHERE zipcode = 07094 AND role = 'ADMIN' AND enabled = true

//And "find the least-loaded Admin for these zipcodes" becomes:
SELECT ra.user_id, COUNT(cr.id) as pending_count
FROM RoleAssignment ra
LEFT JOIN CreatorRequest cr 
  ON cr.assigned_admin_id = ra.user_id AND cr.status = 'pending'
WHERE ra.zipcode IN (07094, 07096, 07047) 
  AND ra.role = 'ADMIN' AND ra.enabled = true
GROUP BY ra.user_id
ORDER BY pending_count ASC