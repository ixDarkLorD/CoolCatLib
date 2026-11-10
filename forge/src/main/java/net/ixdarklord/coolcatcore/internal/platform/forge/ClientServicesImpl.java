package net.ixdarklord.coolcatcore.internal.platform.forge;

import net.ixdarklord.coolcatcore.api.client.registry.MenuScreenRegistry;
import net.ixdarklord.coolcatcore.internal.platform.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Key mappings and tooltip components wait for their Forge registration events, fired on CoolCatLib's mod bus
// (see ForgeClientEventHooks). Forge has no menu screen event: screens go into MenuScreens during client setup, once
// menu types are registered, or straight away after it.
public final class ClientServicesImpl implements ClientServices {
    private static final ClientServicesImpl INSTANCE = new ClientServicesImpl();

    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<Runnable> MENU_SCREENS = new ArrayList<>();
    private static final List<Consumer<RegisterClientTooltipComponentFactoriesEvent>> TOOLTIP_COMPONENTS = new ArrayList<>();
    // Block entity renderers wait for Forge's renderer registration event.
    private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> RENDERERS = new ArrayList<>();
    private static boolean menuScreensRegistered;

    public static ClientServices get() {
        return INSTANCE;
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        synchronized (KEY_MAPPINGS) {
            KEY_MAPPINGS.add(mapping);
        }
    }

    @Override
    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void registerMenuScreen(Supplier<? extends MenuType<? extends M>> type, MenuScreenRegistry.ScreenFactory<M, U> factory) {
        Runnable registration = () -> MenuScreens.<M, U>register(type.get(), factory::create);
        synchronized (MENU_SCREENS) {
            if (!menuScreensRegistered) {
                MENU_SCREENS.add(registration);
                return;
            }
        }
        registration.run();
    }

    @Override
    public <T extends TooltipComponent> void registerTooltipComponent(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
        synchronized (TOOLTIP_COMPONENTS) {
            TOOLTIP_COMPONENTS.add(event -> event.register(type, factory));
        }
    }

    @Override
    public <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T> provider) {
        synchronized (RENDERERS) {
            RENDERERS.add(event -> event.registerBlockEntityRenderer(type.get(), provider));
        }
    }

    // Render layers go into ItemBlockRenderTypes during client setup (see setBlockRenderLayers), once blocks exist; any
    // registered after that go in at once.
    private static final List<Runnable> BLOCK_RENDER_LAYERS = new ArrayList<>();
    private static boolean blockRenderLayersSet;

    @Override
    public void registerBlockRenderLayer(Supplier<? extends Block> block, RenderType layer) {
        Runnable registration = () -> ItemBlockRenderTypes.setRenderLayer(block.get(), layer);
        synchronized (BLOCK_RENDER_LAYERS) {
            if (!blockRenderLayersSet) {
                BLOCK_RENDER_LAYERS.add(registration);
                return;
            }
        }
        Minecraft.getInstance().execute(registration);
    }

    /** Called on the main thread during client setup. */
    public static void setBlockRenderLayers() {
        List<Runnable> pending;
        synchronized (BLOCK_RENDER_LAYERS) {
            blockRenderLayersSet = true;
            pending = List.copyOf(BLOCK_RENDER_LAYERS);
            BLOCK_RENDER_LAYERS.clear();
        }
        pending.forEach(Runnable::run);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ForgeClientNetworking.sendToServer(payload);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        synchronized (KEY_MAPPINGS) {
            KEY_MAPPINGS.forEach(event::register);
        }
    }

    /** Called on the main thread during client setup. */
    public static void registerMenuScreens() {
        List<Runnable> pending;
        synchronized (MENU_SCREENS) {
            menuScreensRegistered = true;
            pending = List.copyOf(MENU_SCREENS);
            MENU_SCREENS.clear();
        }
        pending.forEach(Runnable::run);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        synchronized (RENDERERS) {
            RENDERERS.forEach(registration -> registration.accept(event));
        }
    }

    public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        synchronized (TOOLTIP_COMPONENTS) {
            TOOLTIP_COMPONENTS.forEach(registration -> registration.accept(event));
        }
    }
    // Extra models wait for the model event, which loads each from its id; they're kept under a "standalone" variant.
    private static final List<Function<ResourceManager, Collection<ResourceLocation>>> EXTRA_MODELS = new CopyOnWriteArrayList<>();

    @Override
    public void registerExtraModels(Function<ResourceManager, Collection<ResourceLocation>> provider) {
        EXTRA_MODELS.add(provider);
    }

    @Override
    public BakedModel getExtraModel(ResourceLocation model) {
        return Minecraft.getInstance().getModelManager().getModel(standalone(model));
    }

    private static ModelResourceLocation standalone(ResourceLocation model) {
        return new ModelResourceLocation(model, "standalone");
    }

    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        // The packs being loaded are the game's by now: a reload puts them in place before its listeners run.
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        EXTRA_MODELS.forEach(source -> source.apply(resources).forEach(model -> event.register(standalone(model))));
    }
}
