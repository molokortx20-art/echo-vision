package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.config.EchoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Голос из голосового чата → волна эха. Громкость кадра (20 мс PCM) переводится в децибелы;
 * выше порога — волна от говорящего, тем дальше, чем громче. От одного говорящего — не чаще
 * {@code voiceInterval}, иначе речь сливалась бы в сплошную засветку.
 * <p>
 * Не зависит от API голосовых чатов: вызовы приходят из {@code compat.EchoVoicePlugin} (Simple Voice Chat)
 * и {@code compat.PlasmoVoiceAddon} (Plasmo Voice), из их аудиопотоков.
 */
public final class VoiceEchoes {
	private static final UUID SELF = new UUID(0, 0);
	private static final Map<UUID, Double> LAST = new ConcurrentHashMap<>();

	private VoiceEchoes() {}

	public static void ownVoice(short[] audio, boolean whispering) {
		float volume = volumeOf(SELF, audio, whispering);
		if (volume <= 0) return;
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.player == null) return;
			Vec3d eye = client.player.getEyePos();
			EchoEngine.INSTANCE.voice(eye.x, eye.y, eye.z, volume);
		});
	}

	public static void entityVoice(UUID entityId, short[] audio, boolean whispering) {
		float volume = volumeOf(entityId, audio, whispering);
		if (volume <= 0) return;
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.world == null) return;
			Entity speaker = client.world.getEntity(entityId);
			if (speaker == null) return;
			Vec3d eye = speaker.getEyePos();
			EchoEngine.INSTANCE.voice(eye.x, eye.y, eye.z, volume);
		});
	}

	/** Голос существа по сетевому id (Plasmo Voice: EntitySourceInfo). */
	public static void entityIdVoice(UUID sourceId, int entityId, short[] audio) {
		float volume = volumeOf(sourceId, audio, false);
		if (volume <= 0) return;
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.world == null) return;
			Entity speaker = client.world.getEntityById(entityId);
			if (speaker == null) return;
			Vec3d eye = speaker.getEyePos();
			EchoEngine.INSTANCE.voice(eye.x, eye.y, eye.z, volume);
		});
	}

	public static void locatedVoice(UUID id, double x, double y, double z, short[] audio) {
		float volume = volumeOf(id, audio, false);
		if (volume <= 0) return;
		MinecraftClient.getInstance().execute(() -> EchoEngine.INSTANCE.voice(x, y, z, volume));
	}

	/** Громкость для волны (0 — не слышно или рано), примерно как у игрового звука: 0.2–1.6. */
	private static float volumeOf(UUID speaker, short[] audio, boolean whispering) {
		EchoConfig cfg = EchoConfig.get();
		if (!cfg.echoEnabled || !cfg.voiceEcho || !cfg.catVoice || audio == null || audio.length == 0) return 0;
		double sum = 0;
		for (short s : audio) sum += (double) s * s;
		double rms = Math.sqrt(sum / audio.length) / 32768.0;
		if (rms <= 0) return 0;
		double db = 20 * Math.log10(rms);
		if (db < cfg.voiceThreshold) return 0;

		double now = EchoClock.now();
		Double last = LAST.get(speaker);
		if (last != null && now - last < cfg.voiceInterval) return 0;
		LAST.put(speaker, now);

		// От порога до +30 дБ над ним — от тихой речи до крика.
		float loud = (float) Math.min(1.0, (db - cfg.voiceThreshold) / 30.0);
		float volume = 0.25f + 1.35f * loud;
		return whispering ? volume * 0.4f : volume;
	}
}
