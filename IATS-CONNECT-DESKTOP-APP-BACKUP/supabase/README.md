# Supabase notes
The app targets the existing IATS Connect Supabase project. It uses the existing `student_profiles`, `groups`, `group_memberships`, `catchups`, and `hub_posts` tables. No database migration is automatically run by this desktop app.

Existing RLS rules currently restrict inserts/updates/deletes for `catchups` and `hub_posts` to the admin email `focalforges@gmail.com`. Public read access may need a policy adjustment if you want signed-out visitors to read those rows through the Data API. Do not disable RLS.
