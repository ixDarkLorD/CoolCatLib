package net.ixdarklord.coolcatcore.internal.brewing;

import net.ixdarklord.coolcatcore.api.brewing.BrewingRecipeRegistry;
import net.ixdarklord.coolcatcore.api.event.v1.server.RegisterBrewingRecipesEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

// The brewing recipes mods add in code (RegisterBrewingRecipesEvent), gathered the first time a brewing stand or its
// menu asks: the same on both sides, as they come from code alone.
public final class CustomBrewing {
    private static @Nullable BrewingRecipeRegistry registry;

    private CustomBrewing() {}

    public static BrewingRecipeRegistry registry() {
        BrewingRecipeRegistry made = registry;
        if (made == null) {
            made = new BrewingRecipeRegistry(List.copyOf(RegisterBrewingRecipesEvent.invokeEvent().getBuilder().getBrewingRecipes()));
            registry = made;
        }
        return made;
    }
}
