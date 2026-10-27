package com.dragonyx.echolocation.test;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import com.dragonyx.echolocation.echo.EchoField;
import com.dragonyx.echolocation.echo.VoiceEchoes;
import com.dragonyx.echolocation.screen.EchoColorScreen;
import com.dragonyx.echolocation.screen.EchoConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Строит в мире комнату, щёлкает эхолокацией и снимает скриншоты для проверки глазами. */
public final class EchoClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("EchoTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("echovision.perf")) return;
		context.runOnClient(client -> {
			EchoConfig.reset();
			client.options.getFov().setValue(80);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			String[] scene = {
				"gamemode creative @a",
				"time set midnight",
				"execute as @p at @s run tp @s ~ ~40 ~",
				"execute as @p at @s run fill ~-10 ~-1 ~-10 ~10 ~6 ~10 minecraft:stone hollow",
				"execute as @p at @s run fill ~-9 ~ ~-9 ~9 ~5 ~9 minecraft:air",
				"execute as @p at @s run fill ~-9 ~ ~-3 ~9 ~5 ~-3 minecraft:stone",
				"execute as @p at @s run fill ~-1 ~ ~-3 ~0 ~2 ~-3 minecraft:air",
				"execute as @p at @s run fill ~4 ~ ~-1 ~4 ~4 ~-1 minecraft:oak_planks",
				"execute as @p at @s run fill ~-5 ~ ~0 ~-5 ~2 ~0 minecraft:glass",
				"execute as @p at @s run fill ~-3 ~ ~1 ~-1 ~ ~1 minecraft:oak_fence",
				"execute as @p at @s run setblock ~2 ~ ~1 minecraft:oak_stairs",
				"execute as @p at @s run setblock ~1 ~ ~-2 minecraft:torch",
				"execute as @p at @s run fill ~6 ~ ~2 ~8 ~ ~4 minecraft:white_wool",
				"execute as @p at @s run fill ~-9 ~-1 ~-2 ~0 ~-1 ~9 minecraft:cobblestone",
				"execute as @p at @s run setblock ~10 ~1 ~4 minecraft:diamond_ore",
				"execute as @p at @s run setblock ~10 ~2 ~6 minecraft:iron_ore",
				"execute as @p at @s run setblock ~-10 ~1 ~5 minecraft:coal_ore",
				"execute as @p at @s run setblock ~-6 ~ ~6 minecraft:poppy",
				"execute as @p at @s run setblock ~-7 ~ ~5 minecraft:crafting_table",
				// Комната под полом: её эхо не должно просвечивать сквозь пол («рентген»).
				"execute as @p at @s run fill ~-8 ~-8 ~-8 ~8 ~-2 ~8 minecraft:stone hollow",
				"execute as @p at @s run fill ~2 ~-7 ~2 ~2 ~-3 ~2 minecraft:oak_log",
				"execute as @p at @s run fill ~-3 ~-7 ~-2 ~-3 ~-3 ~-2 minecraft:oak_log",
				"execute as @p at @s run summon minecraft:zombie ~-3 ~ ~-1 {NoAI:1b,Silent:1b}",
				"execute as @p at @s run summon minecraft:cow ~3 ~ ~-7 {NoAI:1b,Silent:1b}",
				// То, что рисуется не моделью блока: блок-сущности, неживые сущности, броня и предметы.
				"execute as @p at @s run setblock ~2 ~ ~-2 minecraft:chest[facing=south]",
				"execute as @p at @s run summon minecraft:oak_boat ~0.5 ~ ~-0.5",
				"execute as @p at @s run summon minecraft:item ~1.5 ~0.2 ~0.5 {Item:{id:\"minecraft:diamond\",count:1},NoGravity:1b,PickupDelay:32767}",
				"execute as @p at @s run summon minecraft:armor_stand ~-1.5 ~ ~-1.5 {Rotation:[200f,0f]}",
				"item replace entity @e[type=minecraft:armor_stand] armor.head with minecraft:iron_helmet",
				"item replace entity @e[type=minecraft:armor_stand] armor.chest with minecraft:diamond_chestplate",
				"item replace entity @e[type=minecraft:zombie] weapon.mainhand with minecraft:iron_sword",
				"item replace entity @e[type=minecraft:zombie] armor.head with minecraft:golden_helmet",
				"item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe",
				"execute as @p at @s run tp @s ~ ~ ~3 180 15"
			};
			for (String cmd : scene) sp.getServer().runCommand(cmd);
			context.waitTicks(20);
			sp.getClientWorld().waitForChunksRender();

			context.runOnClient(client -> EchoConfig.get().blindness = false);
			context.waitTicks(5);
			shot(context, "01_scene_no_blindness");

			context.runOnClient(client -> EchoConfig.get().blindness = true);
			context.waitTicks(5);
			shot(context, "02_blind_dark");

			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(3);
			shot(context, "03_ring_near");
			context.waitTicks(4);
			shot(context, "04_ring_far");
			context.waitTicks(10);
			shot(context, "04b_contours_glow");
			context.runOnClient(client -> LOG.info("Echo blocks: {}, entities: {}",
				EchoField.INSTANCE.blocks.size(), EchoField.INSTANCE.entities.size()));
			context.waitTicks(30);
			shot(context, "05_pulse_fading");

			// Звук за стеной: рычание из соседней комнаты должно пройти через проём.
			context.waitTicks(40);
			sp.getServer().runCommand("execute as @p at @s run playsound minecraft:entity.zombie.ambient hostile @a ~-3 ~ ~-7 2");
			context.waitTicks(6);
			shot(context, "06a_wall_wave_doorway");
			context.waitTicks(4);
			shot(context, "06a_wall_wave_through");
			context.waitTicks(4);
			shot(context, "06_sound_behind_wall");

			// Звук на полу перед игроком: кольцо расходится от источника.
			context.waitTicks(70);
			sp.getServer().runCommand("execute as @p at @s run playsound minecraft:block.anvil.land block @a ~2 ~ ~-4 0.5");
			context.waitTicks(3);
			shot(context, "06b_ring_from_floor");
			context.waitTicks(3);
			shot(context, "06c_ring_from_floor_later");

			// Взгляд в пол после щелчка: комната под полом не должна быть видна.
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 70");
			context.waitTicks(30);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(10);
			shot(context, "09_look_down_no_xray");
			sp.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 15");

			// Голос: громкий синтетический кадр микрофона, как от Simple Voice Chat.
			context.waitTicks(60);
			short[] voice = new short[960];
			for (int i = 0; i < voice.length; i++) voice[i] = (short) (12000 * Math.sin(i * 0.12));
			// Simple Voice Chat присылает кадры из своего аудиопотока — повторяем это.
			Thread audio = new Thread(() -> VoiceEchoes.ownVoice(voice, false), "Fake voice chat audio");
			audio.start();
			try {
				audio.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			context.waitTicks(6);
			shot(context, "10_voice_wave");

			if (FabricLoader.getInstance().isModLoaded("plasmovoice")) {
				context.waitTicks(80);
				try {
					PlasmoVoiceTestHelper.speak(voice);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				context.waitTicks(6);
				shot(context, "11_plasmo_voice_wave");
			}

			// Прыжок на месте: в ванилле беззвучен, волну даёт отталкивание и приземление.
			context.waitTicks(80);
			context.getInput().holdKeyFor(options -> options.jumpKey, 2);
			context.waitTicks(4);
			shot(context, "12_jump_takeoff");
			context.waitTicks(10);
			shot(context, "13_jump_landing");

			// Пустая рука от первого лица и своё тело от третьего лица.
			sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			context.waitTicks(80);
			context.runOnClient(client -> {
				EchoEngine.INSTANCE.pulse();
				LOG.info("Player echo level right after click: {}", EchoField.INSTANCE.entityLevel(client.player.getId()));
			});
			context.waitTicks(4);
			context.runOnClient(client -> LOG.info("Player echo level: {}", EchoField.INSTANCE.entityLevel(client.player.getId())));
			shot(context, "14_empty_hand");
			context.waitTicks(80);
			context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(5);
			shot(context, "15_third_person");
			context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));

			// F5 несколько раз подряд, с бронёй и мечом, пока своё тело светится эхом.
			// Свечение (как на серверах) рисует ещё и обводку: на нём игра падала у игрока.
			sp.getServer().runCommand("effect give @a minecraft:glowing infinite 0 true");
			sp.getServer().runCommand("effect give @e[type=minecraft:zombie] minecraft:glowing infinite 0 true");
			sp.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
			sp.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
			sp.getServer().runCommand("item replace entity @a armor.chest with minecraft:elytra");
			sp.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
			context.waitTicks(10);
			for (int i = 0; i < 8; i++) {
				context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
				context.waitTicks(2);
				context.getInput().pressKey(options -> options.togglePerspectiveKey);
				context.waitTicks(3);
				int n = i;
				context.runOnClient(client -> LOG.info("F5 #{}: {}", n + 1, client.options.getPerspective()));
				if (i < 3) shot(context, "16_f5_" + (i + 1));
			}
			context.runOnClient(client -> client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));

			// Неровный край волны и шерстяной пол перед игроком (шерсть глушит эхо).
			sp.getServer().runCommand("effect clear @a minecraft:glowing");
			sp.getServer().runCommand("execute as @p at @s run fill ~-1 ~-1 ~-2 ~1 ~-1 ~-4 minecraft:white_wool");
			context.runOnClient(client -> EchoConfig.get().waveNoise = 1f);
			context.waitTicks(80);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(4);
			shot(context, "17_wave_noise");
			context.waitTicks(4);
			shot(context, "17b_wave_noise_later");
			context.waitTicks(12);
			shot(context, "17c_wool_floor_dampened");
			context.runOnClient(client -> EchoConfig.get().waveNoise = 0f);

			context.runOnClient(client -> {
				EchoConfig.get().colorMode = EchoConfig.ColorMode.DISTANCE;
				EchoEngine.INSTANCE.reset();
			});
			context.waitTicks(25);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(8);
			shot(context, "07_distance_colors");

			context.setScreen(() -> new EchoConfigScreen(null));
			context.waitTicks(5);
			shot(context, "08_config_screen");
			double cx = context.computeOnClient(client -> client.getWindow().getWidth() / 2.0);
			double cy = context.computeOnClient(client -> client.getWindow().getHeight() / 2.0);
			context.getInput().setCursorPos(cx, cy);
			context.getInput().scroll(-14);
			context.waitTicks(5);
			shot(context, "08a_config_kinds_look");
			context.getInput().scroll(-6);
			context.waitTicks(5);
			shot(context, "08a2_config_look");
			context.setScreen(() -> new EchoColorScreen(null));
			context.waitTicks(5);
			shot(context, "08b_color_screen");
			context.setScreen(() -> null);
			context.runOnClient(client -> EchoConfig.reset());
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		LOG.info("Screenshot {}: {}", name, context.takeScreenshot(name));
	}
}
