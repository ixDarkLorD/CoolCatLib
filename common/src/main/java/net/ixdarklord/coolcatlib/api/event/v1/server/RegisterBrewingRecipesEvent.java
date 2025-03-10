package net.ixdarklord.coolcatlib.api.event.v1.server;

import net.ixdarklord.coolcatlib.api.brewing.BrewingBuilder;
import net.ixdarklord.coolcatlib.api.event.v1.Event;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.jetbrains.annotations.ApiStatus;

public class RegisterBrewingRecipesEvent {
    public static final Event<RegisterBrewingRecipesEvent> EVENT = new Event<>();

    private final BrewingBuilder builder;

    private RegisterBrewingRecipesEvent(PotionBrewing.Builder builder) {
        this.builder = new BrewingBuilder(builder);
    }

    public BrewingBuilder getBuilder() {
        return builder;
    }

    @ApiStatus.Internal
    public static RegisterBrewingRecipesEvent invokeEvent(PotionBrewing.Builder builder) {
        RegisterBrewingRecipesEvent event = new RegisterBrewingRecipesEvent(builder);
        EVENT.invoke(event);
        return event;
    }
}