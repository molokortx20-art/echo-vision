package com.dragonyx.echolocation.test;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.Random;

/**
 * Нагрузка: большой зал со столбами, много звуков подряд и щелчки. Цифры пишет EchoStats
 * ([perf] в логе). Запуск: gradlew runClientGameTest -Pperf
 */
public final class EchoPerfGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("echovision.perf")) return;
		context.runOnClient(client -> EchoConfig.reset());
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~60 ~");
			server.runCommand("execute as @p at @s run fill ~-24 ~-1 ~-24 ~24 ~12 ~24 minecraft:stone hollow");
			for (int x = -20; x <= 20; x += 5) {
				for (int z = -20; z <= 20; z += 5) {
					if (x == 0 && z == 0) continue;
					server.runCommand("execute as @p at @s run fill ~" + x + " ~ ~" + z + " ~" + x + " ~11 ~" + z + " minecraft:cobblestone");
				}
			}
			server.runCommand("execute as @p at @s run fill ~-20 ~ ~-20 ~20 ~ ~20 minecraft:oak_fence replace minecraft:air");
			server.runCommand("execute as @p at @s run fill ~-1 ~ ~-1 ~1 ~ ~1 minecraft:air");
			for (int i = 0; i < 12; i++) {
				server.runCommand("execute as @p at @s run summon minecraft:zombie ~" + (i * 3 - 16) + " ~1 ~" + (i % 4 * 6 - 9) + " {NoAI:1b,Silent:1b}");
			}
			context.waitTicks(20);
			sp.getClientWorld().waitForChunksRender();

			Random random = new Random(42);
			// 40 секунд: звук каждые 2 тика в случайном месте зала, щелчок раз в секунду.
			for (int t = 0; t < 800; t += 2) {
				int x = random.nextInt(36) - 18, z = random.nextInt(36) - 18;
				server.runCommand("execute as @p at @s run playsound minecraft:block.stone.break block @a ~" + x + " ~1 ~" + z + " 1");
				if (t % 20 == 0) context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
				context.waitTicks(2);
			}
			context.takeScreenshot("perf_hall");
			context.runOnClient(client -> EchoConfig.reset());
		}
	}
}
