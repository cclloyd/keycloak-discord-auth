package com.holonet.keycloak.discord;

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

/**
 * Configuration for the Discord Identity Provider.
 */
public class DiscordIdentityProviderConfig extends OAuth2IdentityProviderConfig {

    public static final String CFG_GUILD_ID = "guildId";

    private final IdentityProviderModel model;

    public DiscordIdentityProviderConfig() {
        super();
        this.model = null;
    }

    public DiscordIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
        this.model = model;
    }

    public IdentityProviderModel getModel() {
        return model;
    }

    public String getGuildId() {
        return getConfig() != null ? getConfig().get(CFG_GUILD_ID) : null;
    }

    public void setGuildId(String guildId) {
        getConfig().put(CFG_GUILD_ID, guildId);
    }
}