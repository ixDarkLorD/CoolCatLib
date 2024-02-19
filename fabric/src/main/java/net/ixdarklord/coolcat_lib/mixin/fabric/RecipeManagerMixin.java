package net.ixdarklord.coolcat_lib.mixin.fabric;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.ixdarklord.coolcat_lib.common.crafting.ConditionalRecipe;
import net.ixdarklord.coolcat_lib.common.crafting.CraftingHelper;
import net.ixdarklord.coolcat_lib.common.crafting.ICondition;
import net.ixdarklord.coolcat_lib.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;

@Mixin(value = RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Shadow private boolean hasErrors;

    @SuppressWarnings("rawtypes")
    @Shadow private Map recipes;

    @Shadow private Map<ResourceLocation, Recipe<?>> byName;

    @Shadow @Final private static Logger LOGGER;

    @Inject(method = "apply*", at = @At("HEAD"), cancellable = true)
    private void apply$Inject(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        if (Services.PLATFORM.isModLoaded("kubejs")) return;

        this.hasErrors = false;
        Map<RecipeType<?>, ImmutableMap.Builder<ResourceLocation, Recipe<?>>> map = Maps.newHashMap();
        ImmutableMap.Builder<ResourceLocation, Recipe<?>> builder = ImmutableMap.builder();
        Iterator<Map.Entry<ResourceLocation, JsonElement>> var6 = object.entrySet().iterator();

        while(true) {
            Map.Entry<ResourceLocation, JsonElement> entry;
            ResourceLocation resourcelocation;
            do {
                if (!var6.hasNext()) {
                    //noinspection rawtypes
                    this.recipes = map.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, (entryx) ->
                            ((ImmutableMap.Builder)entryx.getValue()).build()));
                    this.byName = builder.build();
                    LOGGER.info("Loaded {} recipes", map.size());
                    ci.cancel();
                    return;
                }

                entry = var6.next();
                resourcelocation = entry.getKey();
            } while(resourcelocation.getPath().startsWith("_"));

            try {
                if (entry.getValue().isJsonObject() && !CraftingHelper.processConditions(entry.getValue().getAsJsonObject(), "conditions", ICondition.IContext.EMPTY)) {
                    ConditionalRecipe.Serializer.LOGGER.info("Skipping loading recipe {} as it's conditions were not met", resourcelocation);
                } else {
                    Recipe<?> recipe = RecipeManager.fromJson(resourcelocation, GsonHelper.convertToJsonObject(entry.getValue(), "top element"));
                    //noinspection ConstantValue
                    if (recipe == null) {
                        LOGGER.info("Skipping loading recipe {} as it's serializer returned null", resourcelocation);
                    } else {
                        //noinspection rawtypes,unchecked
                        ((ImmutableMap.Builder)map.computeIfAbsent(recipe.getType(), (arg) ->
                                ImmutableMap.builder())).put(resourcelocation, recipe);
                        builder.put(resourcelocation, recipe);
                    }
                }
            } catch (JsonParseException | IllegalArgumentException var10) {
                LOGGER.error("Parsing error loading recipe {}", resourcelocation, var10);
            }
        }
    }
}
