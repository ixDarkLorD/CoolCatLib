package net.ixdarklord.coolcat_lib.util;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;

import static net.minecraft.commands.synchronization.ArgumentTypeInfos.BY_CLASS;

public class ArgumentTypeHelper {
    public static synchronized <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> I register(Class<A> infoClass, I argumentTypeInfo) {
        BY_CLASS.put(infoClass, argumentTypeInfo);
        return argumentTypeInfo;
    }
}
