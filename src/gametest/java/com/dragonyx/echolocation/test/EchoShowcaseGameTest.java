package com.dragonyx.echolocation.test;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import com.dragonyx.echolocation.screen.EchoConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;

import java.nio.file.Path;

/**
 * Витринные скриншоты для страницы мода (Modrinth): зал из глубинного сланца с вардоном,
 * Г-образный туннель, мобы. Запускается только с -Dechovision.showcase=true (gradlew -Pshowcase).
 */
public final class EchoShowcaseGameTest implements FabricClientGameTest {
	private static final int W = 1920, H = 1080;
	private static final Path OUT = Path.of(System.getProperty("echovision.showcaseDir", "showcase"));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("echovision.showcase")) return;
		context.runOnClient(client -> {
			EchoConfig.reset();
			client.options.getFov().setValue(75);
			client.options.hudHidden = true;
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			TestServerContext server = sp.getServer();
			buildScene(server);
			context.waitTicks(40);
			sp.getClientWorld().waitForChunksRender();

			// 1. Зал: до (обычное зрение с ночным видением) и после (слепота + щелчок).
			view(server, 0.5, 150, 10.5, 180, 6);
			context.waitTicks(20);
			context.runOnClient(client -> EchoConfig.get().blindness = false);
			server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
			context.waitTicks(10);
			shot(context, "01_hall_normal_sight");
			server.runCommand("effect clear @a minecraft:night_vision");
			context.runOnClient(client -> EchoConfig.get().blindness = true);
			context.waitTicks(10);
			shot(context, "02_hall_blind");

			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(5);
			shot(context, "03_hall_wave_near");
			context.waitTicks(4);
			shot(context, "04_hall_wave_far");
			context.waitTicks(12);
			shot(context, "05_hall_lit");

			// 2. Кадры для GIF: щелчок и разбегающаяся волна.
			context.waitTicks(80);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			for (int i = 0; i < 18; i++) {
				context.takeScreenshot(TestScreenshotOptions.of(String.format("gif_hall_%02d", i))
					.withSize(960, 540).withDestinationDir(OUT.resolve("gif")).disableCounterPrefix());
				context.waitTicks(2);
			}

			// 3. Туннель: рычание в зале, волна заходит в коридор и огибает поворот.
			context.waitTicks(80);
			view(server, 24.5, 150, -12.5, 0, 4);
			context.waitTicks(30);
			server.runCommand("playsound minecraft:entity.warden.roar hostile @a 2.5 151 0.5 3");
			context.waitTicks(10);
			shot(context, "06_tunnel_wave_arrives");
			context.waitTicks(4);
			shot(context, "07_tunnel_wave_corner");
			context.waitTicks(8);
			shot(context, "08_tunnel_lit");
			context.waitTicks(80);
			server.runCommand("playsound minecraft:entity.warden.roar hostile @a 2.5 151 0.5 3");
			for (int i = 0; i < 18; i++) {
				context.takeScreenshot(TestScreenshotOptions.of(String.format("gif_tunnel_%02d", i))
					.withSize(960, 540).withDestinationDir(OUT.resolve("gif")).disableCounterPrefix());
				context.waitTicks(2);
			}

			// 4. Мобы крупным планом.
			context.waitTicks(80);
			view(server, -2.5, 150, 6.5, 150, 8);
			context.waitTicks(30);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(12);
			shot(context, "09_mobs_close");

			// 5. Вардон на помосте.
			context.waitTicks(80);
			view(server, 0.5, 150, -3.5, 180, -4);
			context.waitTicks(30);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(10);
			shot(context, "10_warden");

			// 6. Экран настроек.
			context.runOnClient(client -> client.options.hudHidden = false);
			context.setScreen(() -> new EchoConfigScreen(null));
			context.waitTicks(5);
			shot(context, "11_settings");
			context.setScreen(() -> null);
			context.runOnClient(client -> EchoConfig.reset());
		}
	}

	private static void view(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).withSize(W, H).withDestinationDir(OUT).disableCounterPrefix());
	}

	/** Зал 25×11×25 из глубинного сланца с колоннами, помостом, скалком и мобами + Г-образный туннель на восток. */
	private static void buildScene(TestServerContext s) {
		String[] cmds = {
			"gamemode creative @a",
			"time set midnight",
			"tp @a 0.5 150 10.5 180 0",
			// Зал.
			"fill -13 149 -13 13 161 13 minecraft:deepslate_bricks hollow",
			"fill -12 149 -12 12 149 12 minecraft:deepslate_tiles",
			"fill -6 149 3 -2 149 9 minecraft:cobbled_deepslate",
			"fill 4 149 -9 9 149 -4 minecraft:sculk",
			"fill -12 149 -2 -9 149 4 minecraft:mossy_cobblestone",
			"fill -13 155 -13 13 155 -13 minecraft:polished_deepslate",
			"fill -13 155 13 13 155 13 minecraft:polished_deepslate",
			// Колонны.
			"fill 6 150 6 7 160 7 minecraft:polished_deepslate",
			"fill -7 150 6 -6 160 7 minecraft:polished_deepslate",
			"fill 6 150 -7 7 160 -6 minecraft:polished_deepslate",
			"fill -7 150 -7 -6 160 -6 minecraft:polished_deepslate",
			"fill 6 150 6 7 150 7 minecraft:chiseled_deepslate",
			"fill -7 150 6 -6 150 7 minecraft:chiseled_deepslate",
			"fill 6 150 -7 7 150 -6 minecraft:chiseled_deepslate",
			"fill -7 150 -7 -6 150 -6 minecraft:chiseled_deepslate",
			// Помост с лестницей.
			"fill -5 150 -12 5 150 -9 minecraft:deepslate_bricks",
			"fill -5 150 -8 5 150 -8 minecraft:deepslate_brick_stairs[facing=north]",
			"fill -5 151 -12 5 151 -12 minecraft:deepslate_brick_wall",
			// Детали.
			"fill 0 156 0 0 160 0 minecraft:iron_chain",
			"setblock 0 155 0 minecraft:lantern[hanging=true]",
			"setblock 10 150 10 minecraft:barrel",
			"setblock 10 151 10 minecraft:barrel",
			"setblock 9 150 10 minecraft:barrel",
			"setblock -10 150 10 minecraft:chest",
			"setblock 6 150 -5 minecraft:sculk_sensor",
			"setblock 8 150 -8 minecraft:sculk_shrieker",
			"fill -12 160 -12 -11 160 -11 minecraft:cobweb",
			"fill 11 160 11 12 160 12 minecraft:cobweb",
			"setblock -3 150 -3 minecraft:candle[lit=false,candles=3]",
			"fill -12 150 8 -12 152 12 minecraft:bookshelf",
			// Туннель на восток с поворотом на север.
			"fill 13 149 -2 26 153 2 minecraft:deepslate_bricks",
			"fill 22 149 -15 26 153 2 minecraft:deepslate_bricks",
			"fill 14 150 -1 25 152 1 minecraft:air",
			"fill 23 150 -14 25 152 1 minecraft:air",
			"fill 13 150 -1 13 152 1 minecraft:air",
			"fill 14 149 -1 25 149 1 minecraft:cobbled_deepslate",
			"fill 23 149 -14 25 149 -1 minecraft:cobbled_deepslate",
			"setblock 24 152 -6 minecraft:cobweb",
			"fill 17 150 1 18 150 1 minecraft:barrel",
			// Мобы (неподвижные и молчаливые — звук даём сами).
			"summon minecraft:warden 0.5 151 -10.5 {NoAI:1b,Silent:1b,Rotation:[0f,0f]}",
			"summon minecraft:zombie -5.5 150 1.5 {NoAI:1b,Silent:1b,Rotation:[200f,0f]}",
			"summon minecraft:skeleton 4.5 150 3.5 {NoAI:1b,Silent:1b,Rotation:[160f,0f]}",
			"summon minecraft:creeper -9.5 150 -3.5 {NoAI:1b,Silent:1b,Rotation:[220f,0f]}",
			"summon minecraft:spider 9.5 150 -1.5 {NoAI:1b,Silent:1b,Rotation:[120f,0f]}",
		};
		for (String cmd : cmds) s.runCommand(cmd);
	}
}
