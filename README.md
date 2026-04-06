# Keycloak Discord Identity Provider (Guild-Gated)

Custom Keycloak SPI identity provider for Discord (Keycloak). Implements guild-gated first login where:
- Existing users already linked to Discord can log in regardless of guild membership. (Done manually through user page on Keycloak admin console)
- Otherwise you can specify a discord guild that the user must be a member of to be allowed to log in.

## Build
```bash
mvn -DskipTests package
```
Copy the built JAR to your Keycloak providers dir (`/opt/keycloak/providers`)


## Discord Setup
1. Create a Discord application at https://discord.com/developers/applications
2. Add an OAuth2 redirect URI matching your Keycloak realm/broker endpoint, e.g.:
   - `https://<your-keycloak-host>/realms/<realm>/broker/discord/endpoint`
3. Note the Client ID and Client Secret.


## Keycloak Setup
1. Drop the JAR into your Keycloak providers directory and restart Keycloak.
2. In the Admin Console → Identity Providers → Create, pick "Discord" (the custom provider from this SPI).
3. Configure:
   - Client ID / Client Secret from Discord Developer Portal
   - Scopes: leave default (`identify email guilds`) or customize
   - Required Discord Guild ID: set to your guild ID.
4. First Broker Login Flow:
   - Use the built-in "First Broker Login" flow, ensure it contains "Review profile" (or "Update profile").
   - This will prompt the user for a username because this provider leaves `username` unset for first-time logins.
   - Email is set from Discord; the user may be prompted to confirm/update if your flow requires it.


## Behavior Details
- Existing Linked Users:
  - If a federated identity link already exists (Discord user ID linked to a local user), login is allowed with no guild check.
- New Users (no link yet):
  - Provider calls Discord `GET /users/@me/guilds` (requires `guilds` scope) and checks for the configured guild ID.
  - If not a member → authentication error is thrown and login is blocked.
  - If a member → Keycloak proceeds with First Broker Login; user account is created with email from Discord and username is requested.
- Email:
  - Requires `email` scope and that the Discord user granted it. If email is missing and your flow requires it, Keycloak will prompt via "Review profile".


## Notes & Tips
- Ensure your realm has a reliable username policy since users will be prompted to choose one.
- If you prefer stricter email handling, add the "Verify Email" required action or execution in First Broker Login.
- Discord scope `guilds` is sufficient to list guilds for the current user. No bot or privileged intents required.


