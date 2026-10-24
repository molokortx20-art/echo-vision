package com.dragonyx.echolocation;

import com.dragonyx.echolocation.compat.PlasmoVoiceAddon;
import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import com.dragonyx.echolocation.render.EchoEntityLayers;
import com.dragonyx.echolocation.render.EchoRenderer;
import com.dragonyx.echolocation.screen.EchoConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EchoMod implements ClientModInitializer {
	public static final String MOD_ID = "echovision";
	public static final Logger LOGGER = LoggerFactory.getLogger("Echo Vision");

	private static KeyBinding toggleKey;
	private static KeyBinding blindKey;
	private static KeyBinding pulseKey;
	private static KeyBinding configKey;

	@Override
	public void onInitializeClient() {
		EchoConfig.load();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.echovision.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, category));
		blindKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.echovision.blindness", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
		pulseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.echovision.pulse", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
		configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.echovision.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		EchoRenderer.init();
		EchoEntityLayers.init();
		if (FabricLoader.getInstance().isModLoaded("plasmovoice")) {
			try {
				PlasmoVoiceAddon.register();
			} catch (Throwable t) {
				LOGGER.warn("Plasmo Voice найден, но аддон эха не загрузился — голос не будет давать волну", t);
			}
		}
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.getSoundManager()
			.registerListener((sound, soundSet, range) -> EchoEngine.INSTANCE.onSound(sound)));
		ClientTickEvents.END_CLIENT_TICK.register(EchoMod::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EchoEngine.INSTANCE.reset());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EchoEngine.INSTANCE.reset());
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(EchoRenderer::render);
	}

	private static void tick(MinecraftClient client) {
		EchoConfig cfg = EchoConfig.get();
		while (toggleKey.wasPressed()) {
			cfg.echoEnabled = !cfg.echoEnabled;
			if (!cfg.echoEnabled) EchoEngine.INSTANCE.reset();
			EchoConfig.save();
			overlay(client, cfg.echoEnabled ? "message.echovision.echo_on" : "message.echovision.echo_off");
		}
		while (blindKey.wasPressed()) {
			cfg.blindness = !cfg.blindness;
			EchoConfig.save();
			overlay(client, cfg.blindness ? "message.echovision.blind_on" : "message.echovision.blind_off");
		}
		while (pulseKey.wasPressed()) {
			if (cfg.echoEnabled) EchoEngine.INSTANCE.pulse();
		}
		while (configKey.wasPressed()) {
			client.setScreen(new EchoConfigScreen(client.currentScreen));
		}
		EchoEngine.INSTANCE.tick(client);
	}

	private static void overlay(MinecraftClient client, String key) {
		if (client.player != null) client.player.sendMessage(Text.translatable(key), true);
	}

	/** Тьма включена прямо сейчас. */
	public static boolean blindActive() {
		EchoConfig cfg = EchoConfig.get();
		return cfg.blindness && MinecraftClient.getInstance().world != null;
	}
}
