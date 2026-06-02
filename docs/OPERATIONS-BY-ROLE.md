- Admins can enable/disable Creators by zipcode(s), county, state or 'ALL' for all Creators.  If a 
  Creator is disabled all submit/save buttons on the 'Creator' UI section (see "GUI:" section below) for that creator return an error message saying "This account has been disabled.  Please check your email {submitter email address}.".  
	  While disabled all subsequent logins are rejected.  The Creator's user account should be disabled, e.g. 
	  'user.isEnabled = false'.
- Admins can:
  - disable/enable specific Poll types for a given zipcode, county or state within their 
    purview.  
  - When a Poll type is disabled for a given zipcode then no more submissions are allowed for all  
    instances of that poll type and zipcode(s), county or state. 

  - Enable/Disable registered users by zipcode(s), county, state or 'ALL' for all Users.  If a registered 
    user is disabled all submit/save buttons on the 'User' UI section return an error message saying 
    "This account has been disabled.  Please check your email {address}.".  When the User reads the message, the message should have an 'OK' button that once clicked by the disabled User will logout the User and subsequent logins are rejected.  The User's user account should be disabled, e.g. 'user.isEnabled = false'.
- Admins can disable all poll type instances filtered by any of creator, zipcode, county, state or date 
  created.  
  These poll type instances should not appear in any search from /polls.  
