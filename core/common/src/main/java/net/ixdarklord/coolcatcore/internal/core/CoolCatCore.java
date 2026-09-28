package net.ixdarklord.coolcatcore.internal.core;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class CoolCatCore {
	public static final String MOD_ID = "coolcatcore";
	public static final String MOD_NAME = "CoolCat Core";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	public static final UnsupportedOperationException OPERATION_EXCEPTION =
			new UnsupportedOperationException("This loader is not supported to do this operation!");

	public static Identifier rl(String name) {
		return Identifier.fromNamespaceAndPath(MOD_ID, name.toLowerCase(Locale.ROOT));
	}

	public static RuntimeException createMixinException(String extension) {
		return new UnsupportedOperationException("Implementation does not support extension: " + extension);
	}
}