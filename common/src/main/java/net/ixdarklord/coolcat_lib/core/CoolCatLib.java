package net.ixdarklord.coolcat_lib.core;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class CoolCatLib {
	public static final String MOD_ID = "coolcat_lib";
	public static final String MOD_NAME = "CoolCatLib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	public static ResourceLocation getLocation(String name) {
		return new ResourceLocation(MOD_ID, name.toLowerCase(Locale.ROOT));
	}
}