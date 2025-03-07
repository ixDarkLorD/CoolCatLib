package net.ixdarklord.coolcatlib.api.core.commands.neoforge;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;

public final class ArgumentTypeRegistryImpl {

    public static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> I register(Class<A> infoClass, I argumentTypeInfo) {
        return ArgumentTypeInfos.registerByClass(infoClass, argumentTypeInfo);
    }
}
