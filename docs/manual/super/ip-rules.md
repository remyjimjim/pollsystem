---
title: IP allow and deny rules
summary: Lists of addresses to allow or block (stored, not enforced yet).
order: 6
---

# IP allow and deny rules

**IP Allow / Deny** keeps lists of internet addresses to allow or block.

**These rules are stored but not enforced yet.** Blocking is deliberately not
switched on, so that a mistake can't lock everyone out. Adding a rule has no
effect on who can use Poll System today.

To add a rule:

1. **Value**: one address (e.g. `203.0.113.7`) or a range in CIDR form (e.g.
   `10.0.0.0/8`).
2. **Type**: **ALLOW** or **DENY**.
3. **Note** (optional): why.
4. **Enabled**: whether the rule is on.
5. Click **Add rule**.

The list shows who added each rule and when. **Enable** / **Disable** switches
a rule on or off, and **Delete** removes it.
