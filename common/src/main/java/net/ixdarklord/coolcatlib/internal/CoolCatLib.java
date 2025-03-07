package net.ixdarklord.coolcatlib.internal;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class CoolCatLib {
	public static final String MOD_ID = "coolcatlib";
	public static final String MOD_NAME = "CoolCatLib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	public static final UnsupportedOperationException OPERATION_EXCEPTION =
			new UnsupportedOperationException("This loader is not supported to do this operation!");

	public static ResourceLocation rl(String name) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, name.toLowerCase(Locale.ROOT));
	}

	public static RuntimeException createMixinException(String extension) {
		return new UnsupportedOperationException("Implementation does not support extension: " + extension);
	}
}