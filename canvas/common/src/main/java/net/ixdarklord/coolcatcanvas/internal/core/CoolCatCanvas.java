package net.ixdarklord.coolcatcanvas.internal.core;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class CoolCatCanvas {
	public static final String MOD_ID = "coolcatcanvas";
	public static final String MOD_NAME = "CoolCatLib: Canvas";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	public static Identifier rl(String name) {
		return Identifier.fromNamespaceAndPath(MOD_ID, name.toLowerCase(Locale.ROOT));
	}
}
