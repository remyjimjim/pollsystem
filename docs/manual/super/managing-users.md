---
title: Managing users
summary: Find any account, switch it on or off, change roles, see their polls, send messages, and delete an account on request.
order: 3
---

# Managing users

**Manage Users** lists members, creators and admins. It doesn't list supers.
People whose membership has lapsed are listed only when you tick **Lapsed**.

## Finding people

Filter by **Role** (tick **Lapsed** to include cancelled or unpaid members),
**Email contains**, **State**, **County**, **Zipcode**, or
words in the **Messages** you've sent them, then click **Search**. Switched-off
accounts are hidden unless you tick **Show disabled**.

## Switching an account off

Use the **Enable/Disable** column to switch an account off or back on. A
switched-off account can't sign in, and if they're already signed in,
everything they try is refused from then on.

To do many at once, press **Shift+\*** in the column header to enable everyone
listed, or **Shift+0** to disable everyone listed. Shift+click between two rows
toggles a range.

## Changing a role

The small **⋮** next to someone's role badge (USER, CREATOR, ADMIN) opens
**Promote to …** and **Demote to …**. Clicking the badge itself opens **Edit**,
where you can also pick a role. Both work the same way.

- **Raising a role** asks for the **area** of the new access: one or more whole
  states, or nationwide.
  - Promoted to **creator**, they get creator access there.
  - Promoted to **admin**, they get admin access there, plus creator access
    across the same area.
- **Lowering a role** switches off the access of the level they left.
  - An admin demoted to creator loses their admin access but keeps their
    creator access.
  - Anyone demoted to member loses their creator access.

You can't promote or demote a super, and admins can't go higher than admin.

**Edit** also changes where someone lives (state, county, zipcode). That's
separate from the access they're granted.

## Their polls and answers

- **Polls Created** lists the polls a person made.
- **Polls Completed** lists the polls they answered. Open one to see their
  answers, **including their comments**.

Comments are private to supers unless the person made one public (it then also
appears anonymously on the results page). Respect that when you read them.

## Messages

**New msg?** writes a message on the person's account, for example to record
why you switched them off. Tick **Send to user?** to email it to them as well.
Messages aren't shown to the user in the app; they only see an emailed copy.

Click a person's message to see their message history, step through it with
**Previous** and **Next**, and edit a message.

## Deleting an account

Members cancel their membership themselves. Deleting is for when someone
**emails asking for their account to be deleted**:

1. Make sure the request comes from the account's own email address.
2. Find them. Tick **Lapsed** if they've already cancelled.
3. Open the **⋮** next to their role and choose **Delete account…**.
4. Type their email to confirm, then click **Delete permanently**.

What happens:

- Their account, answers and comments are deleted, along with their sign-in
  links, messages and any creator or admin requests and access.
- The results of every poll they answered are recounted without them. A poll
  can drop below the minimum number of respondents and hide its results again.
- Any subscription is cancelled at once, without a refund. Stripe keeps its own
  payment records.
- What they did as staff (requests they processed, settings they changed) stays
  on record, without their name.
- They're emailed a confirmation: "Your account data from BallotBox.us.com has
  been deleted per your request."

**It can't be undone.** It's refused for supers and for accounts that own polls,
wrote poll notes, blocks or messages, or switched creators off. Other people's
answers or moderation depend on those, so they need sorting out first.
