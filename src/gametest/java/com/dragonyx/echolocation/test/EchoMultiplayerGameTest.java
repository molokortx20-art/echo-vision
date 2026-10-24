package com.dragonyx.echolocation.test;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoEngine;
import com.dragonyx.echolocation.echo.EchoField;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/** Игра по сети: выделенный сервер, своя рука и тело в эхе. Запуск: gradlew runClientGameTest -Pmultiplayer */
public final class EchoMultiplayerGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("EchoMpTest");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("echovision.multiplayer")) return;
		context.runOnClient(client -> EchoConfig.reset());
		Properties props = new Properties();
		props.setProperty("online-mode", "false");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(props);
			 TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksRender();
			String[] scene = {
				"op @a",
				"gamemode creative @a",
				"execute as @p at @s run tp @s ~ ~40 ~",
				"execute as @p at @s run fill ~-6 ~-1 ~-6 ~6 ~5 ~6 minecraft:stone hollow",
				"execute as @p at @s run setblock ~2 ~ ~-3 minecraft:chest[facing=south]",
				"execute as @p at @s run summon minecraft:zombie ~-2 ~ ~-3 {NoAI:1b,Silent:1b}",
				"execute as @p at @s run tp @s ~ ~ ~3 180 10",
				"item replace entity @a weapon.mainhand with minecraft:air",
			};
			for (String cmd : scene) server.runCommand(cmd);
			context.waitTicks(40);
			connection.getClientWorld().waitForChunksRender();

			// Шаги по полу, как в игре: ходьба вперёд.
			context.getInput().holdKeyFor(options -> options.forwardKey, 12);
			context.waitTicks(3);
			context.runOnClient(client -> LOG.info("MP player echo level after steps: {} (blocks {}, entities {})",
				EchoField.INSTANCE.entityLevel(client.player.getId()), EchoField.INSTANCE.blocks.size(), EchoField.INSTANCE.entities.size()));
			context.takeScreenshot("mp_01_steps");

			context.waitTicks(80);
			context.runOnClient(client -> EchoEngine.INSTANCE.pulse());
			context.waitTicks(5);
			context.runOnClient(client -> LOG.info("MP player echo level after click: {}", EchoField.INSTANCE.entityLevel(client.player.getId())));
			context.takeScreenshot("mp_02_click");
		}
		context.runOnClient(client -> EchoConfig.reset());
	}
}
