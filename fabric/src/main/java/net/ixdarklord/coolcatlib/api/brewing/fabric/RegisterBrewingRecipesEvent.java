package net.ixdarklord.coolcatlib.api.brewing.fabric;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.ixdarklord.coolcatlib.api.core.event.BaseEvent;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.jetbrains.annotations.ApiStatus;

public class RegisterBrewingRecipesEvent extends BaseEvent {
    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(Callback.class, callbacks -> event -> {
        for (Callback callback : callbacks)
            callback.onRegisterBrewingRecipes(event);
    });

    private final BrewingBuilder builder;

    @ApiStatus.Internal
    public RegisterBrewingRecipesEvent(PotionBrewing.Builder builder) {
        this.builder = new BrewingBuilder(builder);
    }

    public BrewingBuilder getBuilder() {
        return builder;
    }

    @Override
    public void sendEvent() {
        EVENT.invoker().onRegisterBrewingRecipes(this);
    }

    public interface Callback {
        void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event);
    }
}