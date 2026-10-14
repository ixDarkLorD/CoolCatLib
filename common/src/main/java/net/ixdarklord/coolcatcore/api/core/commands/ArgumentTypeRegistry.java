package net.ixdarklord.coolcatcore.api.core.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;

public final class ArgumentTypeRegistry {

    @ExpectPlatform
    public static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> I register(Class<A> infoClass, I argumentTypeInfo) {
        throw CoolCatCore.OPERATION_EXCEPTION;
    }

    private ArgumentTypeRegistry() {
        // NO-OP
    }
}
