package com.origin.client;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OriginClient implements ModInitializer {
	public static final String MOD_ID = "originclient";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Origin Client initializing");
	}
}
