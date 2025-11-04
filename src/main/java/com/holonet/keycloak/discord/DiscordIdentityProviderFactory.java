package com.holonet.keycloak.discord;

import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;

import java.util.ArrayList;
import java.util.List;

public class DiscordIdentityProviderFactory extends AbstractIdentityProviderFactory<DiscordIdentityProvider> {

    public static final String PROVIDER_ID = DiscordIdentityProvider.PROVIDER_ID; // "discord"

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty guild = new ProviderConfigProperty();
        guild.setName(DiscordIdentityProviderConfig.CFG_GUILD_ID);
        guild.setLabel("Required Discord Guild ID");
        guild.setHelpText("Only allow first login if the user is a member of this Discord guild. Existing linked users bypass this check. Leave blank to allow all Discord users.");
        guild.setType(ProviderConfigProperty.STRING_TYPE);
        guild.setDefaultValue("");
        CONFIG_PROPERTIES.add(guild);
    }

    @Override
    public String getName() {
        return "Discord";
    }

    @Override
    public DiscordIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new DiscordIdentityProvider(session, new DiscordIdentityProviderConfig(model));
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public DiscordIdentityProviderConfig createConfig() {
        return new DiscordIdentityProviderConfig();
    }
}