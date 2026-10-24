package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.config.EchoConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Звуки движения, которых в ванилле нет: прыжок на месте и мягкое приземление ничего не проигрывают
 * (шагов нет, а звук падения — только при уроне). Здесь отталкивание от земли даёт тихую волну,
 * а приземление — тем сильнее, чем быстрее падало существо. Крадучись — тише.
 */
final class MovementEchoes {
	/** Дальше этого существа не отслеживаем — их волны до игрока всё равно не дойдут. */
	private static final double TRACK_RADIUS = 48;
	private static final float TAKEOFF_VOLUME = 0.3f;

	private static final class State {
		boolean onGround;
		double lastY;
		/** Самая большая скорость падения за полёт, блоков за тик. */
		double fallSpeed;
		int seenTick;
	}

	private final Int2ObjectOpenHashMap<State> states = new Int2ObjectOpenHashMap<>();
	private int tick;

	void tick(MinecraftClient client, ClientWorld world) {
		EchoConfig cfg = EchoConfig.get();
		if (!cfg.echoEnabled || !cfg.movementEcho || client.player == null) {
			states.clear();
			return;
		}
		tick++;
		double r2 = TRACK_RADIUS * TRACK_RADIUS;
		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || living.isSpectator() || !living.isAlive()) continue;
			if (entity.squaredDistanceTo(client.player) > r2) continue;
			State s = states.get(entity.getId());
			boolean ground = entity.isOnGround();
			double y = entity.getY();
			if (s == null) {
				s = new State();
				s.onGround = ground;
				s.lastY = y;
				s.seenTick = tick;
				states.put(entity.getId(), s);
				continue;
			}
			double dy = y - s.lastY;
			s.lastY = y;
			s.seenTick = tick;

			if (s.onGround && !ground && dy > 0.05 && !entity.hasVehicle() && !living.isClimbing()) {
				// Оттолкнулся от земли.
				s.fallSpeed = 0;
				float volume = entity.isSneaking() ? TAKEOFF_VOLUME * 0.4f : TAKEOFF_VOLUME;
				EchoEngine.INSTANCE.movement(entity.getX(), y + 0.05, entity.getZ(), volume, entity.getSoundCategory());
			} else if (!ground) {
				if (-dy > s.fallSpeed) s.fallSpeed = -dy;
			} else if (!s.onGround && !entity.isTouchingWater()) {
				// Приземлился: громкость от скорости удара (прыжок на месте — около 0.4 блока за тик).
				float volume = (float) Math.min(1.6, 0.25 + s.fallSpeed * 1.2);
				if (entity.isSneaking()) volume *= 0.4f;
				EchoEngine.INSTANCE.movement(entity.getX(), y + 0.05, entity.getZ(), volume, entity.getSoundCategory());
				s.fallSpeed = 0;
			}
			s.onGround = ground;
		}
		if ((tick & 63) == 0) {
			ObjectIterator<Int2ObjectMap.Entry<State>> it = states.int2ObjectEntrySet().fastIterator();
			while (it.hasNext()) {
				if (tick - it.next().getValue().seenTick > 40) it.remove();
			}
		}
	}

	void reset() {
		states.clear();
	}
}
