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
import java.util.ArrayList;
import java.util.List;

/** Все настройки мода. Хранятся в config/echovision.json. */
public final class EchoConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("echovision.json");
	/** Файл до переименования мода в Echo Vision — читается, если нового ещё нет. */
	private static final Path OLD_FILE = FabricLoader.getInstance().getConfigDir().resolve("echolocation.json");

	private static EchoConfig instance = new EchoConfig();

	public enum ColorMode { SINGLE, CATEGORY, DISTANCE }

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

	// Вид (всё, что входит в пресет, — см. EchoLook)
	public float echoDuration = 3.0f;
	public float brightness = 1.0f;
	public ColorMode colorMode = ColorMode.SINGLE;
	/** Цвет поверхностей в режиме «один цвет», 0xRRGGBB. */
	public int echoColor = 0xFFFFFF;
	/** Цвет линий: контуров углов, деталей текстуры и кольца фронта. */
	public int lineColor = 0xFFFFFF;
	/** Сколько видно саму текстуру блока (1) вместо ровной заливки цветом (0). */
	public float textureAmount = 1f;
	/** Яркость ровной заливки поверхности, когда текстура приглушена. */
	public float fillAmount = 0.5f;
	/** Мягкие светлые и тёмные пятна по поверхностям (0 — ровно). */
	public float mottle = 0f;
	/** Яркость линий по границам деталей текстуры. */
	public float detailLines = 1f;
	/** Насколько темнеет эхо вдали от игрока (0 — не темнеет). */
	public float distanceFade = 0f;
	/** Неровность края волны, 0 — ровный. */
	public float waveNoise = 0f;
	/** Светящееся кольцо фронта волны, бегущее по поверхностям. */
	public boolean wavefrontRing = true;
	/** Ширина хвоста кольца, блоков. */
	public float ringWidth = 0.4f;
	/** Яркая вспышка в момент, когда фронт волны касается поверхности. */
	public boolean wavefrontFlash = true;
	/** Имя последнего выбранного пресета (для подсказки на экране настроек). */
	public String preset = "echo_vision";
	/** Свои пресеты игрока. */
	public List<EchoPreset> userPresets = new ArrayList<>();
	/** Встроенные пресеты, которые игрок убрал корзиной (их можно вернуть). */
	public List<String> hiddenPresets = new ArrayList<>();
	/** Пока открыты настройки, раз в пару секунд беззвучный щелчок — чтобы видеть изменения. */
	public boolean previewPulses = true;
	/** Материалы отражают по-разному: камень и металл ярче, шерсть, листва и земля тусклее. */
	public boolean materialEcho = true;
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
	/** Множители дальности эха по типу звука (см. SoundKind). */
	public float scaleSteps = 1f;
	public float scaleMining = 1f;
	public float scalePlacing = 1f;
	public float scaleVoice = 1f;
	public float scaleOther = 1f;

	// Голосовой чат (Simple Voice Chat)
	/** Голос — свой микрофон и речь других игроков — тоже рождает волну. */
	public boolean voiceEcho = true;
	/** Порог громкости голоса, дБ от полной шкалы (тише — не слышно). */
	public float voiceThreshold = -42f;
	/** Не чаще одной волны от одного говорящего за столько секунд. */
	public float voiceInterval = 0.35f;

	/** Версия формата настроек: при обновлении мода старые значения переносятся на новые. */
	public int version = 0;
	private static final int CURRENT_VERSION = 4;
	/** Цвет до версии 4 (оттенок и насыщенность) — только для переноса в echoColor, потом null и не сохраняется. */
	private Integer hue;
	private Float saturation;

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

	/** Всё по умолчанию, кроме своих пресетов — их сброс не трогает. */
	public static void reset() {
		List<EchoPreset> keep = instance.userPresets;
		List<String> hidden = instance.hiddenPresets;
		instance = new EchoConfig();
		instance.userPresets = keep;
		instance.hiddenPresets = hidden;
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
			// v3: звук идёт только по воздуху (туннели, проёмы).
			wallPenetration = false;
		}
		if (version < 4 && hue != null) {
			// v4: цвет хранится готовым RGB, у контуров свой цвет.
			echoColor = java.awt.Color.HSBtoRGB(hue / 360f, saturation == null ? 0f : saturation, 1f) & 0xFFFFFF;
		}
		hue = null;
		saturation = null;
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
		maxBlocks = Math.max(1000, Math.min(200000, maxBlocks));
		pulseRange = clamp(pulseRange, 4, 96);
		pulseCooldown = clamp(pulseCooldown, 0, 10);
		echoColor &= 0xFFFFFF;
		lineColor &= 0xFFFFFF;
		textureAmount = clamp(textureAmount, 0, 1);
		fillAmount = clamp(fillAmount, 0, 1);
		mottle = clamp(mottle, 0, 1);
		detailLines = clamp(detailLines, 0, 2);
		distanceFade = clamp(distanceFade, 0, 1);
		if (preset == null) preset = "";
		if (userPresets == null) userPresets = new ArrayList<>();
		if (hiddenPresets == null) hiddenPresets = new ArrayList<>();
		userPresets.removeIf(p -> p == null || p.name == null || p.name.isBlank() || p.look == null);
		waveNoise = clamp(waveNoise, 0, 1);
		scaleSteps = clamp(scaleSteps, 0, 2);
		scaleMining = clamp(scaleMining, 0, 2);
		scalePlacing = clamp(scalePlacing, 0, 2);
		scaleVoice = clamp(scaleVoice, 0, 2);
		scaleOther = clamp(scaleOther, 0, 2);
		ringWidth = clamp(ringWidth, 0.1f, 4);
		if (colorMode == null) colorMode = ColorMode.SINGLE;
		voiceThreshold = clamp(voiceThreshold, -70, -5);
		voiceInterval = clamp(voiceInterval, 0.05f, 3);
	}

	private static float clamp(float v, float lo, float hi) {
		return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}
}
