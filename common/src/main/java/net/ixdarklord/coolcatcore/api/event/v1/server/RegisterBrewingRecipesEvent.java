package net.ixdarklord.coolcatcore.api.event.v1.server;

import net.ixdarklord.coolcatcore.api.brewing.BrewingBuilder;
import net.ixdarklord.coolcatcore.api.event.v1.Event;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers brewing recipes. On 1.20.1 brewing recipes are global, so this fires once per game: in Forge's common
 * setup, and on Fabric once the game has started (the client, or a dedicated server as it starts). Register listeners
 * while mods are constructed.
 */
public class RegisterBrewingRecipesEvent {
    public static final Event<RegisterBrewingRecipesEvent> EVENT = new Event<>();

    private final BrewingBuilder builder;

    private RegisterBrewingRecipesEvent() {
        this.builder = new BrewingBuilder();
    }

    public BrewingBuilder getBuilder() {
        return builder;
    }

    @ApiStatus.Internal
    public static RegisterBrewingRecipesEvent invokeEvent() {
        RegisterBrewingRecipesEvent event = new RegisterBrewingRecipesEvent();
        EVENT.invoke(event);
        return event;
    }
}
