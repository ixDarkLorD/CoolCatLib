package net.ixdarklord.coolcatcore.internal.platform.fabric;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.function.Function;
import java.util.function.Supplier;

public final class ClientServicesImpl implements ClientServices {
    private static final ClientServices INSTANCE = new ClientServicesImpl();

    public static ClientServices get() {
        return INSTANCE;
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        KeyBindingHelper.registerKeyBinding(mapping);
    }

    // Menu types are registered by now on Fabric (the main entrypoint runs first), so the screen goes in straight away.
    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void registerMenuScreen(Supplier<? extends MenuType<? extends M>> type, MenuScreenRegistry.ScreenFactory<M, U> factory) {
        MenuScreens.register(type.get(), factory::create);
    }

    @Override
    public <T extends TooltipComponent> void registerTooltipComponent(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        TooltipComponentCallback.EVENT.register(data -> type.isInstance(data) ? factory.apply(type.cast(data)) : null);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNetworking.send(payload);
    }
    // Extra models go in through Fabric's model loading plugin, registered once, which asks the providers at every load.
    private static final java.util.List<java.util.function.Function<net.minecraft.server.packs.resources.ResourceManager, java.util.Collection<net.minecraft.resources.ResourceLocation>>> EXTRA_MODELS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean modelPluginRegistered;

    @Override
    public synchronized void registerExtraModels(java.util.function.Function<net.minecraft.server.packs.resources.ResourceManager, java.util.Collection<net.minecraft.resources.ResourceLocation>> provider) {
        EXTRA_MODELS.add(provider);
        if (!modelPluginRegistered) {
            modelPluginRegistered = true;
            net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context -> {
                // The packs being loaded are the game's by now: a reload puts them in place before its listeners run.
                net.minecraft.server.packs.resources.ResourceManager resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
                EXTRA_MODELS.forEach(source -> context.addModels(source.apply(resources)));
            });
        }
    }

    @Override
    public net.minecraft.client.resources.model.BakedModel getExtraModel(net.minecraft.resources.ResourceLocation model) {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(model);
    }
}
