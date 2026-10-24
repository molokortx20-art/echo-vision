package com.dragonyx.echolocation.test;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Нагрузка и FPS: обычный мир, потом большой зал со столбами и много звуков подряд.
 * Цифры мода пишет EchoStats ([perf] в логе), FPS по фазам — этот тест ([fps]).
 * Запуск: gradlew runClientGameTest -Pperf (лучше без Dynamic FPS — он душит FPS свёрнутого окна).
 */
public final class EchoPerfGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("EchoPerf");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("echovision.perf")) return;
		context.runOnClient(client -> {
			EchoConfig.reset();
			client.options.getMaxFps().setValue(260);
			client.options.getEnableVsync().setValue(false);
			// Без ввода игра через минуту считает игрока отошедшим и режет FPS до 30.
			client.options.getInactivityFpsLimit().setValue(net.minecraft.client.option.InactivityFpsLimit.MINIMIZED);
			client.options.getViewDistance().setValue(12);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			var server = sp.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("time set noon");
			server.runCommand("execute as @p at @s run tp @s ~ ~2 ~ 30 10");
			// Дальние чанки догружаются долго — просто ждём, а не требуем, чтобы готово было всё.
			context.waitTicks(300);

			// Фазы на поверхности: что именно съедает кадры.
			phase(context, "A vanilla (echo off, no blindness)", c -> { c.echoEnabled = false; c.blindness = false; }, null);
			phase(context, "B blindness only", c -> { c.echoEnabled = false; c.blindness = true; }, null);
			phase(context, "C blindness + echo, silence", c -> { c.echoEnabled = true; c.blindness = true; }, null);
			phase(context, "B2 blindness only (again)", c -> { c.echoEnabled = false; c.blindness = true; }, null);
			phase(context, "A2 vanilla (again)", c -> { c.echoEnabled = false; c.blindness = false; }, null);
			phase(context, "C2 blindness + echo, silence (again)", c -> { c.echoEnabled = true; c.blindness = true; }, null);
			phase(context, "C3 echo on, no blindness", c -> { c.echoEnabled = true; c.blindness = false; }, null);
			phase(context, "D blindness + echo, click every second", c -> { c.echoEnabled = true; c.blindness = true; }, t -> {
				if (t % 20 == 0) context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			});
			Random random = new Random(7);
			phase(context, "E blindness + echo, sound every 2 ticks", c -> { c.echoEnabled = true; c.blindness = true; }, t -> {
				if (t % 2 == 0) {
					int x = random.nextInt(30) - 15, z = random.nextInt(30) - 15;
					server.runCommand("execute as @p at @s run playsound minecraft:block.stone.break block @a ~" + x + " ~ ~" + z + " 1");
				}
				if (t % 20 == 0) context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			});
			phase(context, "F vanilla again", c -> { c.echoEnabled = false; c.blindness = false; }, null);
			context.takeScreenshot("perf_surface");

			// Зал со столбами: наихудший случай для отрисовки эха.
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
			context.waitTicks(100);
			phase(context, "G hall, vanilla", c -> { c.echoEnabled = false; c.blindness = false; }, null);
			phase(context, "H hall, echo + sound every 2 ticks", c -> { c.echoEnabled = true; c.blindness = true; }, t -> {
				if (t % 2 == 0) {
					int x = random.nextInt(36) - 18, z = random.nextInt(36) - 18;
					server.runCommand("execute as @p at @s run playsound minecraft:block.stone.break block @a ~" + x + " ~1 ~" + z + " 1");
				}
				if (t % 20 == 0) context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			});
			context.takeScreenshot("perf_hall");
			context.runOnClient(client -> EchoConfig.reset());
		}
	}

	private interface Tick {
		void run(int t);
	}

	/** 8 секунд: 2 на разгон, потом средний FPS за 6 секунд по счётчику кадров. */
	private static void phase(ClientGameTestContext context, String name, java.util.function.Consumer<EchoConfig> setup, Tick tick) {
		context.runOnClient(client -> {
			setup.accept(EchoConfig.get());
			EchoEngine.INSTANCE.reset();
		});
		long[] sum = new long[2];
		for (int t = 0; t < 160; t++) {
			// Счётчик FPS игры обновляется раз в секунду — берём его каждую секунду после разгона.
			if (t >= 50 && t % 20 == 10) context.runOnClient(client -> {
				sum[0] += client.getCurrentFps();
				sum[1]++;
			});
			if (tick != null) tick.run(t);
			context.waitTick();
		}
		double fps = (double) sum[0] / Math.max(1, sum[1]);
		LOG.info(String.format("[fps] %-45s %6.1f fps (%.2f ms/frame)", name, fps, 1000 / Math.max(1, fps)));
	}
}
