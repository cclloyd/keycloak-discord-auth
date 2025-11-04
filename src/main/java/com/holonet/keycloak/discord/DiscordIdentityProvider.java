package com.holonet.keycloak.discord;

import org.jboss.logging.Logger;
import org.keycloak.broker.oauth.OAuth2IdentityProvider;
import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.Map;

/**
 * Discord OAuth2 Identity Provider with guild-membership gating on first login.
 *
 * Requirements implemented:
 * - Only allow first-time logins if the user is a member of a specific guild.
 * - If the user is already linked in Keycloak (federated identity exists), allow login regardless of guild.
 * - For first-time logins, set email from Discord and do not set username to let First Broker Login flow prompt the user.
 */
public class DiscordIdentityProvider extends OAuth2IdentityProvider {

    private static final Logger LOG = Logger.getLogger(DiscordIdentityProvider.class);

    public static final String AUTH_URL = "https://discord.com/api/oauth2/authorize";
    public static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    public static final String USERINFO_URL = "https://discord.com/api/users/@me";
    public static final String USER_GUILDS_URL = "https://discord.com/api/users/@me/guilds";

    public static final String PROVIDER_ID = "discord";

    public DiscordIdentityProvider(KeycloakSession session, DiscordIdentityProviderConfig config) {
        super(session, config);
        // Ensure default endpoints if not configured explicitly
        OAuth2IdentityProviderConfig base = getConfig();
        if (base.getAuthorizationUrl() == null) base.setAuthorizationUrl(AUTH_URL);
        if (base.getTokenUrl() == null) base.setTokenUrl(TOKEN_URL);
        if (base.getUserInfoUrl() == null) base.setUserInfoUrl(USERINFO_URL);
        if (base.getDefaultScope() == null || base.getDefaultScope().isBlank()) {
            base.setDefaultScope(getDefaultScopes());
        }
    }

    @Override
    public DiscordIdentityProviderConfig getConfig() {
        return (DiscordIdentityProviderConfig) super.getConfig();
    }

    @Override
    protected String getDefaultScopes() {
        // identify + email for user info; guilds for listing user's guilds
        return "identify email guilds";
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        // Fetch Discord user profile
        Map<String, Object> profile = fetchJson(getConfig().getUserInfoUrl(), accessToken);
        if (profile == null) {
            throw new IdentityBrokerException("Failed to obtain Discord user profile");
        }

        String discordId = getAsString(profile.get("id"));
        if (discordId == null) {
            throw new IdentityBrokerException("Discord user profile missing 'id'");
        }

        BrokeredIdentityContext context = new BrokeredIdentityContext(discordId, getConfig().getModel());
        context.setIdp(this);

        // Username fields: Discord may provide 'global_name' and 'username'
        String username = getAsString(profile.get("username"));
        String globalName = getAsString(profile.get("global_name"));
        String display = globalName != null ? globalName : username;

        // Strip trailing numbers from username
        String cleanUsername = username;
        String trailingNumbers = "0";
        if (cleanUsername != null) {
            // Extract trailing numbers
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)$");
            java.util.regex.Matcher matcher = pattern.matcher(cleanUsername);
            if (matcher.find()) {
                trailingNumbers = matcher.group(1);
            }
            cleanUsername = cleanUsername.replaceAll("\\d+$", "");
        }

        // Set username to Discord username (without discriminator and trailing numbers)
        context.setUsername(cleanUsername);
        context.setName(display);
        context.setEmail(getAsString(profile.get("email"))); // requires email scope

        // Set first and last name from Discord
        // Try to get first_name and last_name from Discord profile
        String firstName = getAsString(profile.get("first_name"));
        String lastName = getAsString(profile.get("last_name"));
        
        // If first_name is not found, use username as first name
        if (firstName == null || firstName.isBlank()) {
            firstName = cleanUsername;
            lastName = trailingNumbers;
        } else {
            // Strip trailing numbers from first name and use them as last name
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)$");
            java.util.regex.Matcher matcher = pattern.matcher(firstName);
            String firstNameNumbers = "0";
            if (matcher.find()) {
                firstNameNumbers = matcher.group(1);
            }
            firstName = firstName.replaceAll("\\d+$", "");
            
            // If last_name is not found, use the numbers from first name
            if (lastName == null || lastName.isBlank()) {
                lastName = firstNameNumbers;
            }
        }
        
        context.setFirstName(firstName);
        context.setLastName(lastName);

        // Determine if this federated identity is already linked to a local user
        RealmModel realm = session.getContext().getRealm();
        String providerAlias = getConfig().getAlias();
        UserModel existing = session.users().getUserByFederatedIdentity(
                realm,
                new FederatedIdentityModel(providerAlias, discordId, null)
        );

        if (existing != null) {
            // Already linked → allow regardless of guild
            LOG.debugf("Discord user %s already linked to local user %s; skipping guild check.", discordId, existing.getId());
            return context;
        }

        // First-time login: enforce guild membership
        String requiredGuildId = getRequiredGuildId();
        if (requiredGuildId != null && !requiredGuildId.isBlank()) {
            if (!isMemberOfGuild(requiredGuildId, accessToken)) {
                throw new IdentityBrokerException("Discord account is not a member of the required guild.");
            }
        }

        return context;
    }

    private String getRequiredGuildId() {
        String guildId = getConfig().getGuildId();
        if (guildId == null || guildId.isBlank()) {
            // Hard default per requirements if not set in admin console
            guildId = "342165983642910720";
        }
        return guildId;
    }

    private boolean isMemberOfGuild(String guildId, String accessToken) {
        try {
            String body = SimpleHttp.doGet(USER_GUILDS_URL, session)
                    .header("Authorization", "Bearer " + accessToken)
                    .asString();
            java.util.List<java.util.Map<String, Object>> guilds = JsonSerialization.readValue(body, java.util.List.class);
            if (guilds == null) return false;
            for (java.util.Map<String, Object> g : guilds) {
                Object id = g.get("id");
                if (id != null && guildId.equals(String.valueOf(id))) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            LOG.warnf(e, "Failed to list Discord guilds for membership check (guild %s)", guildId);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchJson(String url, String accessToken) {
        try {
            String body = SimpleHttp.doGet(url, session)
                    .header("Authorization", "Bearer " + accessToken)
                    .asString();
            return JsonSerialization.readValue(body, Map.class);
        } catch (IOException e) {
            LOG.warnf(e, "Error calling Discord API: %s", url);
            return null;
        }
    }

    private static String getAsString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}