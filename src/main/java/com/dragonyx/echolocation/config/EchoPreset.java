package com.dragonyx.echolocation.config;

import java.util.List;

/**
 * Пресет вида. Встроенные ({@link #BUILT_IN}) нельзя удалить; свои хранятся в настройках
 * (EchoConfig.userPresets) и перезаписываются сохранением под тем же именем.
 */
public final class EchoPreset {
	/** Для встроенных — ключ перевода (preset.echovision.&lt;id&gt;), для своих — имя, которое дал игрок. */
	public String name;
	public EchoLook look;
	/** Встроенный пресет (в файл не пишется). */
	public transient boolean builtIn;

	public EchoPreset() {}

	public EchoPreset(String name, EchoLook look) {
		this.name = name;
		this.look = look;
	}

	private static EchoPreset builtIn(String id, EchoLook look) {
		EchoPreset p = new EchoPreset(id, look);
		p.builtIn = true;
		return p;
	}

	/** Эхо-зрение: текстура в оттенках серого и светлые линии деталей — вид по умолчанию. */
	private static EchoLook echoVision() {
		return new EchoLook();
	}

	/** Сонар: тёмно-синие поверхности, яркие голубые контуры углов, мягкий неровный край волны. */
	private static EchoLook sonar() {
		EchoLook l = new EchoLook();
		l.echoColor = 0x33D1FF;
		l.lineColor = 0x5FE0FF;
		l.textureAmount = 0.12f;
		l.fillAmount = 0.55f;
		l.edgeGlow = 1.1f;
		l.edgeWidth = 0.035f;
		l.detailLines = 0.7f;
		l.distanceFade = 0.45f;
		l.waveNoise = 0.8f;
		l.ringWidth = 0.9f;
		l.wavefrontFlash = false;
		l.brightness = 1.1f;
		l.echoDuration = 3.5f;
		return l;
	}

	/** Чертёж: почти без текстуры, белые линии углов на синем. */
	private static EchoLook blueprint() {
		EchoLook l = new EchoLook();
		l.echoColor = 0x2A5BD7;
		l.lineColor = 0xEAF2FF;
		l.textureAmount = 0f;
		l.fillAmount = 0.35f;
		l.edgeGlow = 1.3f;
		l.edgeWidth = 0.03f;
		l.detailLines = 0.25f;
		l.distanceFade = 0.3f;
		l.ringWidth = 0.5f;
		return l;
	}

	/** Прибор ночного видения: зелёный, с текстурой и мягким затемнением вдали. */
	private static EchoLook nightVision() {
		EchoLook l = new EchoLook();
		l.echoColor = 0x6CFF7A;
		l.lineColor = 0xC8FFCF;
		l.textureAmount = 0.75f;
		l.fillAmount = 0.3f;
		l.edgeGlow = 0.4f;
		l.detailLines = 0.8f;
		l.distanceFade = 0.6f;
		l.waveNoise = 0.3f;
		l.ringWidth = 0.6f;
		return l;
	}

	/** Тепловизор: тёплый оранжевый, яркие края, без вспышки. */
	private static EchoLook thermal() {
		EchoLook l = new EchoLook();
		l.echoColor = 0xFF8A2B;
		l.lineColor = 0xFFE08A;
		l.textureAmount = 0.35f;
		l.fillAmount = 0.5f;
		l.edgeGlow = 0.8f;
		l.detailLines = 0.5f;
		l.distanceFade = 0.5f;
		l.waveNoise = 0.5f;
		l.ringWidth = 0.8f;
		l.wavefrontFlash = false;
		return l;
	}

	public static final List<EchoPreset> BUILT_IN = List.of(
		builtIn("echo_vision", echoVision()),
		builtIn("sonar", sonar()),
		builtIn("blueprint", blueprint()),
		builtIn("night_vision", nightVision()),
		builtIn("thermal", thermal())
	);
}
