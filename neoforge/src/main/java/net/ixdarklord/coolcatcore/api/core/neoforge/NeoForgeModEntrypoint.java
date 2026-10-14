package net.ixdarklord.coolcatcore.api.core.neoforge;

import net.ixdarklord.coolcatcore.api.core.ClientModConstructor;
import net.ixdarklord.coolcatcore.api.core.DataGenerationConstructor;
import net.ixdarklord.coolcatcore.api.core.ModConstructor;
import net.ixdarklord.coolcatcore.api.core.ServerModConstructor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

/**
 * A base for a mod's {@code @Mod} class that constructs its common mod constructors, taking the mod id from the
 * container. One class covers every side:
 * <pre>{@code
 * @Mod(MyMod.MOD_ID)
 * public final class MyModNeoForge extends NeoForgeModEntrypoint {
 *     public MyModNeoForge(ModContainer container) {
 *         super(container);
 *         this.common(MyMod::new);
 *         this.client(() -> MyModClient::new);
 *         this.dataGeneration(MyModData::new);
 *     }
 * }
 * }</pre>
 * Client and server constructors are given as a supplier of a factory, so their classes are never loaded on the
 * other side. NeoForge-only setup can use {@link #container} and {@link #modEventBus} in the same constructor.
 */
public abstract class NeoForgeModEntrypoint {
    protected final ModContainer container;
    protected final IEventBus modEventBus;
    protected final String modId;

    protected NeoForgeModEntrypoint(ModContainer container) {
        this.container = container;
        this.modEventBus = container.getEventBus();
        this.modId = container.getModId();
    }

    /** Constructs the common entry point, on both sides. */
    protected final void common(Supplier<? extends ModConstructor> factory) {
        ModConstructor.construct(this.modId, factory);
    }

    /** Constructs the client entry point, only on a client. */
    protected final void client(Supplier<Supplier<? extends ClientModConstructor>> factory) {
        if (FMLEnvironment.getDist().isClient()) ClientModConstructor.construct(this.modId, factory.get());
    }

    /** Constructs the dedicated server entry point, only on a dedicated server. */
    protected final void server(Supplier<Supplier<? extends ServerModConstructor>> factory) {
        if (FMLEnvironment.getDist().isDedicatedServer()) ServerModConstructor.construct(this.modId, factory.get());
    }

    /** Registers the data generation entry point; the factory is only called when data is generated. */
    protected final void dataGeneration(Supplier<? extends DataGenerationConstructor> factory) {
        DataGenerationConstructor.construct(this.modId, factory);
    }
}
