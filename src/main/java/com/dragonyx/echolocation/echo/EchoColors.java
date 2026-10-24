package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.config.EchoConfig;
import net.minecraft.sound.SoundCategory;

/** Цвета эха. */
public final class EchoColors {
	private EchoColors() {}

	public static int forCategory(SoundCategory category) {
		EchoConfig cfg = EchoConfig.get();
		if (cfg.colorMode != EchoConfig.ColorMode.CATEGORY) return cfg.baseColor();
		return switch (category) {
			case PLAYERS -> 0xE8FAFF;
			case BLOCKS -> 0x6FD4FF;
			case HOSTILE -> 0xFF4D4D;
			case NEUTRAL -> 0x7DFF8C;
			case AMBIENT -> 0xB48CFF;
			case WEATHER -> 0x7088FF;
			case RECORDS -> 0xFFD36B;
			case VOICE -> 0xFF9AE0;
			default -> cfg.baseColor();
		};
	}

	/** Ближнее — белое, дальнее — глубокий синий. */
	public static int byDistance(float d, float range) {
		float t = Math.max(0f, Math.min(1f, d / range));
		int r = (int) (255 * (1 - t) + 30 * t);
		int g = (int) (255 * (1 - t) + 70 * t);
		int b = 255;
		return r << 16 | g << 8 | b;
	}

	public static int pulse() {
		EchoConfig cfg = EchoConfig.get();
		return cfg.colorMode == EchoConfig.ColorMode.CATEGORY ? 0xFFFFFF : cfg.baseColor();
	}
}
