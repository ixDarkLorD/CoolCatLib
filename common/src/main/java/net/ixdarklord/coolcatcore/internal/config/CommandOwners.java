package net.ixdarklord.coolcatcore.internal.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.ixdarklord.coolcatcore.api.command.ModCommands;
import net.ixdarklord.coolcatcore.internal.core.CoolCatCore;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

// Which mod's commands are which, so a mod's config command joins its own commands. ModCommands names them. Commands
// registered through the deprecated CommandEvents.REGISTER are found instead: they belong to the mod whose configs are
// built in the same packages ("/folios" registered from net.ixdarklord.folios.quest, its configs built in
// net.ixdarklord.folios.config).
public final class CommandOwners {
    // What a command name may be: a single word, as Brigadier reads one.
    public static final Pattern NAME = Pattern.compile("[0-9A-Za-z_.+-]+");
    // The commands registered through ModCommands, in order, and every name each mod gave them.
    private static final List<Declared> DECLARED = new CopyOnWriteArrayList<>();
    private static final Map<String, List<String>> DECLARED_NAMES = new ConcurrentHashMap<>();
    // Which mod each dispatcher's ModCommands names went to (server and client dispatchers alike).
    private static final Map<CommandDispatcher<?>, Map<String, String>> REGISTERED = Collections.synchronizedMap(new WeakHashMap<>());
    // The client commands registered through ModCommands, in order.
    private static final List<DeclaredClient> DECLARED_CLIENT = new CopyOnWriteArrayList<>();
    // The packages that build each mod's configs.
    private static final Map<String, Set<String>> CONFIG_PACKAGES = new ConcurrentHashMap<>();
    // Each dispatcher's new top-level commands, with the package of the listener that added them. A dispatcher is made
    // again on every reload, so the old ones go with theirs.
    private static final Map<CommandDispatcher<?>, Map<String, String>> ROOTS = Collections.synchronizedMap(new WeakHashMap<>());
    // The fewest package parts a command's and a config's packages share for both to be one mod's: "net.ixdarklord" alone
    // is every one of the author's mods.
    private static final int MIN_SHARED_PARTS = 3;

    private CommandOwners() {}

    /** Keeps a ModCommands registration, after checking its names. */
    public static void declare(String modId, List<String> names, ModCommands.Builder builder) {
        checkNames(modId, names);
        DECLARED.add(new Declared(modId, List.copyOf(names), builder));
        addNames(DECLARED_NAMES, modId, names);
    }

    /** Keeps a ModCommands client registration, after checking its names. */
    public static void declareClient(String modId, List<String> names, ModCommands.ClientBuilder builder) {
        checkNames(modId, names);
        DECLARED_CLIENT.add(new DeclaredClient(modId, List.copyOf(names), null, builder));
    }

    /** Keeps a ModCommands client registration named after the mod's server command (its names found when built). */
    public static void declareClient(String modId, ModCommands.ClientNames mode, ModCommands.ClientBuilder builder) {
        DECLARED_CLIENT.add(new DeclaredClient(modId, null, mode, builder));
    }

    private static void checkNames(String modId, List<String> names) {
        if (names.isEmpty()) throw new IllegalArgumentException(modId + "'s command needs a name");
        for (String name : names) {
            if (!NAME.matcher(name).matches()) throw new IllegalArgumentException(modId + "'s command name \"" + name + "\" isn't a single word");
        }
    }

    private static void addNames(Map<String, List<String>> declaredNames, String modId, List<String> names) {
        declaredNames.compute(modId, (id, known) -> {
            List<String> all = known == null ? new ArrayList<>() : new ArrayList<>(known);
            for (String name : names) if (!all.contains(name)) all.add(name);
            return List.copyOf(all);
        });
    }

    /** Every name the mod gave its server commands through ModCommands. */
    public static List<String> declaredNames(String modId) {
        return DECLARED_NAMES.getOrDefault(modId, List.of());
    }

    /** Every name of the mod's client commands registered through ModCommands, in order. */
    public static List<String> declaredClientNames(String modId) {
        List<String> all = new ArrayList<>();
        for (DeclaredClient declared : DECLARED_CLIENT) {
            if (!declared.modId().equals(modId)) continue;
            for (String name : declared.names()) if (!all.contains(name)) all.add(name);
        }
        return all;
    }

    /**
     * Builds every ModCommands client registration into the client's dispatcher, as {@link #registerDeclared} does the
     * server's: a name another client command has already is skipped with a warning. Sharing a server command's name is
     * fine: the loaders send what the client's parts can't run on to the server (ClientCommandFallback).
     */
    public static void registerDeclaredClient(CommandDispatcher<SharedSuggestionProvider> dispatcher, CommandBuildContext context) {
        Map<String, String> owners = REGISTERED.computeIfAbsent(dispatcher, key -> new ConcurrentHashMap<>());
        for (DeclaredClient declared : DECLARED_CLIENT) {
            for (String name : declared.names()) {
                if (!claim(dispatcher, owners, declared.modId(), name)) continue;
                try {
                    LiteralArgumentBuilder<SharedSuggestionProvider> root = LiteralArgumentBuilder.literal(name);
                    declared.builder().build(root, context);
                    dispatcher.register(root);
                    owners.put(name, declared.modId());
                } catch (RuntimeException e) {
                    CoolCatCore.LOGGER.error("{}'s client command /{} couldn't be registered", declared.modId(), name, e);
                }
            }
        }
    }

    // Whether the mod may register the name in the dispatcher: free, or the mod's already (registered again to add parts).
    private static boolean claim(CommandDispatcher<?> dispatcher, Map<String, String> owners, String modId, String name) {
        String owner = owners.get(name);
        if (dispatcher.getRoot().getChild(name) == null || modId.equals(owner)) return true;
        CoolCatCore.LOGGER.warn("{}'s command /{} is skipped: {} has that name already", modId, name, owner != null ? owner : "another command");
        return false;
    }

    /**
     * Builds every ModCommands registration into the dispatcher: the whole command under each of its names. A name
     * another command has already (vanilla's {@code /tm}, another mod's) is skipped with a warning: joining it would
     * mix the two, and Brigadier would keep the other's permission and forwarding.
     */
    public static void registerDeclared(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        Map<String, String> owners = REGISTERED.computeIfAbsent(dispatcher, key -> new ConcurrentHashMap<>());
        for (Declared declared : DECLARED) {
            for (String name : declared.names()) {
                if (!claim(dispatcher, owners, declared.modId(), name)) continue;
                try {
                    LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);
                    declared.builder().build(root, context, selection);
                    dispatcher.register(root);
                    owners.put(name, declared.modId());
                } catch (RuntimeException e) {
                    CoolCatCore.LOGGER.error("{}'s command /{} couldn't be registered", declared.modId(), name, e);
                }
            }
        }
    }

    /**
     * The names of the mod's commands for its config command: the ones it registered through ModCommands (that weren't
     * skipped), or else the ones found among the deprecated CommandEvents.REGISTER's.
     */
    public static <S> List<String> names(CommandDispatcher<S> dispatcher, String modId) {
        List<String> declared = declaredNames(modId);
        if (declared.isEmpty()) return roots(dispatcher, modId);
        Map<String, String> owners = REGISTERED.getOrDefault(dispatcher, Map.of());
        return declared.stream().filter(name -> modId.equals(owners.get(name))).toList();
    }

    /** Notes the package of the mod code building a config for the mod: the first caller outside CoolCatLib. */
    public static void configBuilt(String modId) {
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(type -> !type.getPackageName().startsWith("net.ixdarklord.coolcatcore."))
                        .findFirst())
                .ifPresent(type -> CONFIG_PACKAGES.computeIfAbsent(modId, id -> ConcurrentHashMap.newKeySet()).add(type.getPackageName()));
    }

    /** Runs a command listener, noting the top-level commands it adds as its package's. */
    public static <S> void register(CommandDispatcher<S> dispatcher, Object listener, Runnable register) {
        Set<String> before = new HashSet<>();
        for (CommandNode<S> node : dispatcher.getRoot().getChildren()) before.add(node.getName());
        register.run();
        String owner = listener.getClass().getPackageName();
        for (CommandNode<S> node : dispatcher.getRoot().getChildren()) {
            if (before.contains(node.getName())) continue;
            ROOTS.computeIfAbsent(dispatcher, key -> new LinkedHashMap<>()).putIfAbsent(node.getName(), owner);
        }
    }

    /**
     * The mod's own top-level commands in the dispatcher, in the order they were added. Commands that only forward to
     * another ({@code /tw} to {@code /tierwise}) are left out: they reach that one's config command already.
     */
    public static <S> List<String> roots(CommandDispatcher<S> dispatcher, String modId) {
        List<String> roots = new ArrayList<>();
        Map<String, String> added = ROOTS.get(dispatcher);
        if (added == null || !CONFIG_PACKAGES.containsKey(modId)) return roots;
        synchronized (ROOTS) {
            added.forEach((name, listenerPackage) -> {
                CommandNode<S> node = dispatcher.getRoot().getChild(name);
                if (node != null && node.getRedirect() == null && modId.equals(owner(listenerPackage))) roots.add(name);
            });
        }
        return roots;
    }

    // The mod whose config packages share the most parts with the package, or null when none shares enough or two
    // share as many.
    private static @Nullable String owner(String listenerPackage) {
        String best = null;
        int bestShared = 0;
        boolean tie = false;
        for (Map.Entry<String, Set<String>> entry : CONFIG_PACKAGES.entrySet()) {
            int shared = 0;
            for (String configPackage : entry.getValue()) shared = Math.max(shared, sharedParts(listenerPackage, configPackage));
            if (shared > bestShared) {
                best = entry.getKey();
                bestShared = shared;
                tie = false;
            } else if (shared == bestShared && shared > 0) {
                tie = true;
            }
        }
        return best != null && !tie && bestShared >= MIN_SHARED_PARTS ? best : null;
    }

    private record Declared(String modId, List<String> names, ModCommands.Builder builder) {}

    // A client registration: its own names, or a mode taking the mod's server command's (or its id, without one).
    private record DeclaredClient(String modId, @Nullable List<String> fixedNames, ModCommands.@Nullable ClientNames mode,
                                  ModCommands.ClientBuilder builder) {
        List<String> names() {
            if (this.fixedNames != null) return this.fixedNames;
            List<String> serverNames = declaredNames(this.modId);
            return (serverNames.isEmpty() ? List.of(this.modId) : serverNames).stream().map(this.mode::apply).toList();
        }
    }

    private static int sharedParts(String first, String second) {
        String[] a = first.split("\\.");
        String[] b = second.split("\\.");
        int shared = 0;
        while (shared < a.length && shared < b.length && a[shared].equals(b[shared])) shared++;
        return shared;
    }
}
