package net.ixdarklord.coolcatlib.api.event.v1.server;

import net.ixdarklord.coolcatlib.api.brewing.BrewingBuilder;
import net.ixdarklord.coolcatlib.api.event.v1.Event;
import org.jetbrains.annotations.ApiStatus;

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