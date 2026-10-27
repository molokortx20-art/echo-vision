package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.sound.SoundJob;
import com.dragonyx.echolocation.sound.SoundPropagator;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Принимает звуки, считает их распространение в фоновом потоке и передаёт готовые волны в {@link EchoField}.
 */
public final class EchoEngine {
	public static final EchoEngine INSTANCE = new EchoEngine();

	/** Звуки из одной клетки чаще этого интервала сливаются в один (шаги, многослойные звуки). */
	private static final double DEDUPE_SECONDS = 0.12;
	/** -Decholocation.debug=true — писать в лог каждый услышанный звук и каждую волну. */
	private static final boolean DEBUG = Boolean.getBoolean("echovision.debug");

	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
		new ArrayBlockingQueue<>(32), r -> {
			Thread t = new Thread(r, "Echolocation propagation");
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		}, new ThreadPoolExecutor.DiscardOldestPolicy());
	private final SoundPropagator propagator = new SoundPropagator();
	private final ConcurrentLinkedQueue<Finished> finished = new ConcurrentLinkedQueue<>();
	private final Long2DoubleOpenHashMap recent = new Long2DoubleOpenHashMap();
	private final MovementEchoes movementEchoes = new MovementEchoes();
	private double lastPulse = -100;

	private record Finished(ClientWorld world, SoundJob job) {}

	private EchoEngine() {}

	/** Вызывается для каждого звука, который играет клиент. */
	public void onSound(SoundInstance sound) {
		EchoConfig cfg = EchoConfig.get();
		if (DEBUG) EchoMod.LOGGER.info("Sound {} {} at {} {} {}", sound.getId(), sound.getCategory(), sound.getX(), sound.getY(), sound.getZ());
		if (!cfg.echoEnabled) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return;
		if (sound.isRelative() || sound.getAttenuationType() == SoundInstance.AttenuationType.NONE) return;
		if (!categoryEnabled(cfg, sound.getCategory())) return;

		float volume;
		try {
			volume = sound.getVolume();
		} catch (Throwable t) {
			volume = 1f;
		}
		if (!(volume > 0.001f)) return;

		emit(client, sound.getX(), sound.getY(), sound.getZ(), volume, sound.getCategory(), SoundKind.of(sound.getId()), true);
	}

	/** Прыжок или приземление (см. {@link MovementEchoes}), в потоке клиента. */
	void movement(double x, double y, double z, float volume, SoundCategory category) {
		EchoConfig cfg = EchoConfig.get();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || !categoryEnabled(cfg, category)) return;
		if (DEBUG) EchoMod.LOGGER.info("Movement at {} {} {}, volume {}", x, y, z, volume);
		emit(client, x, y, z, volume, category, SoundKind.STEP, true);
	}

	/** Голос из голосового чата (уже в потоке клиента). */
	public void voice(double x, double y, double z, float volume) {
		EchoConfig cfg = EchoConfig.get();
		MinecraftClient client = MinecraftClient.getInstance();
		if (!cfg.echoEnabled || client.world == null || client.player == null) return;
		if (DEBUG) EchoMod.LOGGER.info("Voice at {} {} {}, volume {}", x, y, z, volume);
		emit(client, x, y, z, volume, SoundCategory.VOICE, SoundKind.VOICE, false);
	}

	/**
	 * Звук громкостью volume → волна. Чем громче, тем дальше (в пределах maxRange);
	 * у каждого типа звука свой множитель дальности.
	 */
	private void emit(MinecraftClient client, double x, double y, double z, float volume, SoundCategory category,
					  SoundKind kind, boolean dedupe) {
		EchoConfig cfg = EchoConfig.get();
		float range = Math.min(cfg.maxRange, Math.max(3f,
			cfg.baseRange * (float) Math.sqrt(Math.min(4f, Math.max(0.05f, volume))) * cfg.rangeMultiplier * kind.scale(cfg)));
		// Звук, который до нас никак не дойдёт, не считаем.
		if (client.player.squaredDistanceTo(x, y, z) > (range * 1.5 + 8) * (range * 1.5 + 8)) return;

		double now = EchoClock.now();
		if (dedupe) {
			long key = BlockPos.asLong((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
			double last = recent.getOrDefault(key, -100);
			if (now - last < DEDUPE_SECONDS) return;
			recent.put(key, now);
		}

		float intensity = 0.45f + 0.55f * Math.min(1f, volume);
		submit(client.world, x, y, z, range, intensity, EchoColors.forCategory(category), now);
	}

	/** Активный щелчок языком: волна из глаз игрока. */
	public boolean pulse() {
		EchoConfig cfg = EchoConfig.get();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) return false;
		double now = EchoClock.now();
		if (now - lastPulse < cfg.pulseCooldown) return false;
		lastPulse = now;
		Vec3d eye = client.player.getEyePos();
		submit(client.world, eye.x, eye.y, eye.z, cfg.pulseRange, 1f, EchoColors.pulse(), now);
		if (cfg.pulseSound) {
			// Звук интерфейса не позиционный — слушатель его пропустит, второй волны не будет.
			client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.9f, 0.5f));
		}
		return true;
	}

	/** Беззвучный щелчок для предпросмотра на экране настроек: без перезарядки. */
	public void previewPulse() {
		EchoConfig cfg = EchoConfig.get();
		MinecraftClient client = MinecraftClient.getInstance();
		if (!cfg.echoEnabled || client.world == null || client.player == null) return;
		Vec3d eye = client.player.getEyePos();
		submit(client.world, eye.x, eye.y, eye.z, cfg.pulseRange, 1f, EchoColors.pulse(), EchoClock.now());
	}

	private void submit(ClientWorld world, double x, double y, double z, float range, float intensity, int color, double now) {
		EchoConfig cfg = EchoConfig.get();
		SoundJob job = new SoundJob(x, y, z, range, intensity, color, now,
			cfg.wallAbsorption, cfg.wallPenetration, Math.min(cfg.maxBlocks, 60_000));
		executor.execute(() -> {
			try {
				long t0 = EchoStats.ENABLED ? System.nanoTime() : 0;
				propagator.propagate(world, job);
				if (EchoStats.ENABLED) EchoStats.propagation(System.nanoTime() - t0);
			} catch (Throwable t) {
				// Мир меняется параллельно — редкий сбой одной волны не страшен.
				EchoMod.LOGGER.debug("Echo propagation failed", t);
			}
			finished.add(new Finished(world, job));
		});
	}

	/** Каждый тик клиента. */
	public void tick(MinecraftClient client) {
		long t0 = EchoStats.ENABLED ? System.nanoTime() : 0;
		tickWaves(client);
		if (EchoStats.ENABLED) EchoStats.tick(System.nanoTime() - t0);
	}

	private void tickWaves(MinecraftClient client) {
		ClientWorld world = client.world;
		Finished f;
		while ((f = finished.poll()) != null) {
			if (world == null || f.world() != world) continue;
			SoundJob job = f.job();
			// Сам игрок тоже: его рука и тело видны в эхе собственных звуков.
			List<Entity> nearby = world.getOtherEntities(null,
				Box.of(new Vec3d(job.x, job.y, job.z), job.range * 2, job.range * 2, job.range * 2));
			if (DEBUG) EchoMod.LOGGER.info("Wave at {} {} {}: range {}, surfaces {}, complete {}",
				job.originX, job.originY, job.originZ, job.range, job.result.size, job.result.complete);
			EchoField.INSTANCE.addWave(job, nearby);
		}
		if (world != null) movementEchoes.tick(client, world);
		EchoField.INSTANCE.prune();
		if (recent.size() > 512) {
			double now = EchoClock.now();
			recent.long2DoubleEntrySet().removeIf(e -> now - e.getDoubleValue() > DEDUPE_SECONDS);
		}
	}

	public void reset() {
		finished.clear();
		recent.clear();
		movementEchoes.reset();
		EchoField.INSTANCE.clear();
		SoundPropagator.clearCache();
	}

	private static boolean categoryEnabled(EchoConfig cfg, SoundCategory category) {
		return switch (category) {
			case PLAYERS -> cfg.catPlayers;
			case BLOCKS -> cfg.catBlocks;
			case HOSTILE -> cfg.catHostile;
			case NEUTRAL -> cfg.catNeutral;
			case AMBIENT -> cfg.catAmbient;
			case WEATHER -> cfg.catWeather;
			case RECORDS -> cfg.catRecords;
			case VOICE -> cfg.catVoice;
			default -> false;
		};
	}
}
