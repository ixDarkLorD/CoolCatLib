package net.ixdarklord.coolcatlib.api.core.commands.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import net.fabricmc.fabric.mixin.command.ArgumentTypeInfosAccessor;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;

public final class ArgumentTypeRegistryImpl {

    @SuppressWarnings("UnstableApiUsage")
    public static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> I register(Class<A> infoClass, I argumentTypeInfo) {
        ArgumentTypeInfosAccessor.fabric_getClassMap().put(infoClass, argumentTypeInfo);
        return argumentTypeInfo;
    }
}
