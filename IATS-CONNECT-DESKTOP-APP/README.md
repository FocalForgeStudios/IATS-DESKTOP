# IATS Connect Desktop
Desktop JavaFX application for the Institute of Advanced Technical Studies (IATS), using red and blue accents in light mode and deep navy with restrained red accents in dark mode.

## Requirements
- Java 21
- Maven 3.9+

## Configure Supabase
1. Copy `config.properties.example` to `config.properties` in the project root.
2. In Supabase Dashboard → Project Settings → API, copy the **publishable key** (or legacy `anon` key) into `supabase.anon.key`.
3. Never use a `service_role` or secret key in this desktop client. Do not commit `config.properties`.
4. The project URL is already set to the existing IATS Connect project.

## Run
Open the folder containing `pom.xml` in IntelliJ IDEA, let Maven import dependencies, then run `src/main/java/com/iatsconnect/IatsConnectApplication.java`. Or run `mvn javafx:run` from the project root.

## Current features
- Branded desktop shell, left navigation, responsive content cards, light/dark theme.
- Separate email/password sign-in and registration screens.
- Supabase Auth REST integration for sign-in, sign-up, and password reset email request.
- Dashboard, news, events, groups, profile, messages, study buddies, and separate admin area UI.
- Admin area visibility checks the configured admin email; the database's existing RLS policies remain the real security boundary.

## Important configuration notes
- Supabase email/password signup can require email confirmation. Confirmation and password reset email delivery depend on the SMTP/auth email settings in your Supabase project. Supabase's default sender is rate-limited; configure a suitable SMTP provider for real users.
- The current database policies restrict creating/updating/deleting `catchups` and `hub_posts` to `focalforges@gmail.com`. Change those policies deliberately if your administrator account is different; changing the UI setting alone does not grant database permissions.
- Some social features are currently UI foundations; live messaging, study-buddy matching, and news/event CRUD require follow-up implementation against the chosen schema.

## Architecture
- `ui/AppShell` is only the frame (sidebar, top bar, central area). Every screen is a `ui/View` registered in `ui/ViewRegistry.defaults()`; to add one, implement `View` and register it. Access rules (`PUBLIC`, `SIGNED_IN`, `ADMIN`) are enforced by the shell. Screens: `ProfileView` (StudentProfile), `GroupsView` (Group), `SessionView`/`SignInView`/`RegisterView` (Session), plus dashboard, news/events, admin, settings.
- `services/SupabaseRestClient` and `AuthService` throw only `ApiException` (kind + friendly message). Timeouts: 10 s connect, 20 s per request. GET/DELETE retry up to 3 times on network errors, timeouts, 429 and 5xx; an expired token is refreshed once automatically, otherwise the user is signed out with a dialog.
- `cache/DataRepository` is network-first with an offline fallback to `cache/LocalCache` (JSON files in `~/.iats-connect/cache`, override with `cache.dir`, disable with `cache.enabled=false`). Private data is cached per user and deleted on sign-out. Auth tokens are never written to disk.
- Writes (save profile, publish news/events) need a connection; there is no offline write queue.

## Tests
`mvn test` runs the service and cache tests against a local fake Supabase server.


## Feature tables and security migration (important)

The schema file now includes the database foundations for direct messages, group messages, study-buddy requests, and learning-material metadata, plus RLS policies. **Review and apply this SQL to your Supabase project before using these screens or tables.** It is additive, but existing policies with different names may remain active. Check the Supabase SQL editor output and Security Advisor afterwards. The desktop app must only use a publishable/anon key; never add a secret/service-role key to `config.properties`.

The desktop app still requires a full local verification run: `mvn test` and `mvn javafx:run`. A successful ZIP integrity check is not a substitute for a successful Java compile or live Supabase test.


## Upgrade notes (2026-10-12)

This build replaces the Groups placeholder with live group creation, listing, joining, and owner deletion; adds direct-message sending/conversation loading; and adds a study-buddy request directory with course/cohort/availability fields. The REST client now sends modern `sb_publishable_...` keys only through `apikey` and sends `Authorization: Bearer` only when it has an actual user access token.

### Important remaining verification

This is an upgrade candidate, not a certified production release. Run the tests and live integration checklist before using real student data. The SQL migration is additive and must be reviewed against existing policies before applying. Direct messages currently select the recipient by UUID; a student search/picker is still needed for usability. Group messaging and learning-material file upload/storage integration are not yet complete. Do not advertise those as implemented. The application still requires a successful `mvn test` and `mvn javafx:run` on a machine with Maven and JavaFX dependencies.
