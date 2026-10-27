package com.dragonyx.echolocation.echo;

import com.dragonyx.echolocation.config.EchoConfig;
import net.minecraft.util.Identifier;

/**
 * Тип звука для отдельной настройки дальности эха: шаги (и прыжки), копание, установка блоков,
 * голос и всё остальное. Определяется по имени звука.
 */
public enum SoundKind {
	STEP, MINING, PLACING, VOICE, OTHER;

	public float scale(EchoConfig cfg) {
		return switch (this) {
			case STEP -> cfg.scaleSteps;
			case MINING -> cfg.scaleMining;
			case PLACING -> cfg.scalePlacing;
			case VOICE -> cfg.scaleVoice;
			case OTHER -> cfg.scaleOther;
		};
	}

	public static SoundKind of(Identifier id) {
		if (id == null) return OTHER;
		String path = id.getPath();
		if (path.contains("step")) return STEP;
		if (path.contains(".break") || path.endsWith("break") || path.contains(".hit") || path.endsWith(".hit")) return MINING;
		if (isPlacing(path)) return PLACING;
		return OTHER;
	}

	private static boolean isPlacing(String path) {
		if (path.contains(".place") || path.endsWith("place") || path.contains("_place")) return true;
		return switch (path) {
			// Звуки, которые по смыслу — тоже «поставил что-то»: вылил ведро, зажёг свечу, вставил предмет.
			case "item.bucket.empty", "item.bucket.empty_lava", "item.bucket.empty_powder_snow", "item.bottle.empty",
				"block.end_portal_frame.fill", "block.respawn_anchor.charge", "block.composter.fill",
				"block.composter.fill_success", "block.candle.add_candle", "block.cake.add_candle",
				"entity.item_frame.add_item", "entity.glow_item_frame.add_item" -> true;
			default -> false;
		};
	}
}
