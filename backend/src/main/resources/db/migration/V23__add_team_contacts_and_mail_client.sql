-- Contacts with a team instead of a person: an email to "Hiring team" or a recruiting inbox
-- has nobody to file it under. Such a contact names the team and belongs to an application,
-- whose company says whose team it is.
ALTER TABLE contacts ALTER COLUMN person_id DROP NOT NULL;
ALTER TABLE contacts ADD COLUMN team varchar(100);
-- Exactly one of the two: a person, or a team.
ALTER TABLE contacts ADD CONSTRAINT ck_contacts_person_or_team CHECK ((person_id IS NULL) <> (team IS NULL));

-- Where "Email" buttons open a message: GMAIL (a compose window in the browser) or
-- MAIL_APP (a mailto link, for the device's own mail program).
ALTER TABLE users ADD COLUMN mail_client varchar(20) NOT NULL DEFAULT 'GMAIL';
