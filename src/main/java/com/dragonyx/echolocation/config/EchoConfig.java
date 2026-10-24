package com.dragonyx.echolocation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.dragonyx.echolocation.EchoMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Все настройки мода. Хранятся в config/echovision.json. */
public final class EchoConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("echovision.json");
	/** Файл до переименования мода в Echo Vision — читается, если нового ещё нет. */
	private static final Path OLD_FILE = FabricLoader.getInstance().getConfigDir().resolve("echolocation.json");

	private static EchoConfig instance = new EchoConfig();

	public enum ColorMode { SINGLE, CATEGORY, DISTANCE }

	/**
	 * ECHO_VISION — поверхности с текстурой в оттенках серого и линиями деталей,
	 * CONTOURS — только углы и рёбра предметов, GRID — сетка всех граней, FILL — залитые грани.
	 */
	public enum Style { ECHO_VISION, CONTOURS, GRID, FILL }

	// Основное
	public boolean echoEnabled = true;
	public boolean blindness = true;
	/** Сколько блоков вокруг видно без эха (0 = полная тьма). */
	public float visionRadius = 0.0f;

	// Звук
	/** Скорость звука, блоков в секунду. Настоящая — 343, но тогда волну не разглядеть. */
	public float soundSpeed = 40.0f;
	/** Дальность звука громкостью 1.0, блоков. */
	public float baseRange = 18.0f;
	public float rangeMultiplier = 1.0f;
	public float maxRange = 40.0f;
	/** Пропускают ли стены звук (с затуханием). */
	public boolean wallPenetration = false;
	/** Сколько блоков пути «стоит» один блок камня. */
	public float wallAbsorption = 12.0f;

	// Вид
	public float echoDuration = 3.0f;
	public float brightness = 1.0f;
	public float fillOpacity = 0.25f;
	public float lineWidth = 0.05f;
	public Style style = Style.ECHO_VISION;
	/** Белые контуры углов и рёбер поверх эхо-зрения. */
	public boolean visionContours = false;
	public ColorMode colorMode = ColorMode.SINGLE;
	/** Оттенок (0–359) и насыщенность (0 — белый) цвета эха в режиме «один цвет». */
	public int hue = 190;
	public float saturation = 0f;
	/** Светящееся кольцо фронта волны, бегущее по поверхностям. */
	public boolean wavefrontRing = true;
	/** Ширина хвоста кольца, блоков. */
	public float ringWidth = 0.4f;
	/** Яркая вспышка в момент, когда фронт волны касается поверхности. */
	public boolean wavefrontFlash = true;
	public boolean showEntities = true;
	public boolean showSources = true;
	public int maxBlocks = 30000;

	// Щелчок (активная эхолокация)
	public float pulseRange = 22.0f;
	public float pulseCooldown = 0.8f;
	public boolean pulseSound = true;

	// Какие звуки слышно
	public boolean catPlayers = true;
	public boolean catBlocks = true;
	public boolean catHostile = true;
	public boolean catNeutral = true;
	public boolean catAmbient = true;
	public boolean catWeather = false;
	public boolean catRecords = true;
	public boolean catVoice = true;
	/** Прыжки и приземления (в ванилле почти беззвучные) тоже дают волну. */
	public boolean movementEcho = true;

	// Голосовой чат (Simple Voice Chat)
	/** Голос — свой микрофон и речь других игроков — тоже рождает волну. */
	public boolean voiceEcho = true;
	/** Порог громкости голоса, дБ от полной шкалы (тише — не слышно). */
	public float voiceThreshold = -42f;
	/** Не чаще одной волны от одного говорящего за столько секунд. */
	public float voiceInterval = 0.35f;

	/** Версия формата настроек: при обновлении мода старые значения переносятся на новые. */
	public int version = 0;
	private static final int CURRENT_VERSION = 3;

	public int baseColor() {
		return java.awt.Color.HSBtoRGB(hue / 360f, saturation, 1f) & 0xFFFFFF;
	}

	public static EchoConfig get() {
		return instance;
	}

	public static void load() {
		Path source = Files.exists(FILE) ? FILE : OLD_FILE;
		if (Files.exists(source)) {
			try (Reader r = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
				EchoConfig loaded = GSON.fromJson(r, EchoConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				EchoMod.LOGGER.warn("Не удалось прочитать {}, беру настройки по умолчанию", FILE, e);
			}
		}
		instance.migrate();
		instance.clamp();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (IOException e) {
			EchoMod.LOGGER.warn("Не удалось сохранить {}", FILE, e);
		}
	}

	public static void reset() {
		instance = new EchoConfig();
		instance.version = CURRENT_VERSION;
		save();
	}

	private void migrate() {
		if (version < 2) {
			// v2: эхо ближе к игроку (было 24/64/6/28) — меньше «рентгена» далёких пещер.
			baseRange = 18.0f;
			maxRange = 40.0f;
			wallAbsorption = 12.0f;
			pulseRange = 22.0f;
		}
		if (version < 3) {
			// v3: звук идёт только по воздуху (туннели, проёмы), без лишних контуров поверх эхо-зрения.
			wallPenetration = false;
			visionContours = false;
		}
		version = CURRENT_VERSION;
	}

	private void clamp() {
		visionRadius = clamp(visionRadius, 0, 32);
		soundSpeed = clamp(soundSpeed, 2, 343);
		baseRange = clamp(baseRange, 4, 96);
		rangeMultiplier = clamp(rangeMultiplier, 0.1f, 4);
		maxRange = clamp(maxRange, 8, 96);
		wallAbsorption = clamp(wallAbsorption, 1, 50);
		echoDuration = clamp(echoDuration, 0.3f, 20);
		brightness = clamp(brightness, 0.05f, 3);
		fillOpacity = clamp(fillOpacity, 0, 1);
		lineWidth = clamp(lineWidth, 0.005f, 0.3f);
		maxBlocks = Math.max(1000, Math.min(200000, maxBlocks));
		pulseRange = clamp(pulseRange, 4, 96);
		pulseCooldown = clamp(pulseCooldown, 0, 10);
		if (style == null) style = Style.ECHO_VISION;
		hue = Math.floorMod(hue, 360);
		saturation = clamp(saturation, 0, 1);
		ringWidth = clamp(ringWidth, 0.1f, 4);
		if (colorMode == null) colorMode = ColorMode.SINGLE;
		voiceThreshold = clamp(voiceThreshold, -70, -5);
		voiceInterval = clamp(voiceInterval, 0.05f, 3);
	}

	private static float clamp(float v, float lo, float hi) {
		return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
