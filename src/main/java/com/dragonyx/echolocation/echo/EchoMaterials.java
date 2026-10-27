package com.dragonyx.echolocation.echo;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.BlockSoundGroup;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Насколько хорошо поверхность отражает звук: камень, металл и стекло звенят ярко, дерево — чуть тише,
 * земля и песок приглушают, а шерсть, листва, снег и мох глушат эхо. Материал узнаём по звуку шагов блока.
 */
public final class EchoMaterials {
	public static final float HARD = 1.15f, NORMAL = 1f, WOOD = 0.8f, EARTH = 0.7f, SOFT = 0.45f;

	/** Звуковых групп в игре немного — считаем каждую один раз (только в потоке рендера). */
	private static final Map<BlockSoundGroup, Float> CACHE = new IdentityHashMap<>();

	private EchoMaterials() {}

	public static float reflectivity(BlockState state) {
		if (state.isAir()) return NORMAL;
		// Листва звучит как трава, но глушит сильнее земли.
		if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.WOOL) || state.isIn(BlockTags.WOOL_CARPETS)) return SOFT;
		BlockSoundGroup group = state.getSoundGroup();
		Float cached = CACHE.get(group);
		if (cached == null) {
			cached = byStepSound(group.getStepSound().id().getPath());
			CACHE.put(group, cached);
		}
		return cached;
	}

	/** path — вида «block.wool.step»: смотрим на имя материала посередине. */
	static float byStepSound(String path) {
		String m = path.startsWith("block.") ? path.substring(6) : path;
		int dot = m.indexOf('.');
		if (dot > 0) m = m.substring(0, dot);
		if (m.contains("wool") || m.contains("moss") || m.contains("snow") || m.contains("sculk") || m.contains("leaves")
			|| m.contains("slime") || m.contains("honey") || m.contains("wart") || m.contains("roots")
			|| m.contains("vine") || m.contains("fungus") || m.contains("azalea") || m.contains("dripleaf") || m.contains("cobweb")
			|| m.contains("leaf_litter") || m.contains("carpet") || m.contains("spore") || m.contains("bush")
			|| m.equals("crop") || m.equals("nether_sprouts") || m.equals("lily_pad") || m.equals("shroomlight")) {
			return SOFT;
		}
		// Земля, трава, гравий, песок (у песка в имени и soul_sand): рыхлое, но не глухое.
		if (m.equals("grass") || m.equals("gravel") || m.equals("rooted_dirt") || m.equals("mud") || m.contains("sand")
			|| m.contains("soul_soil")) {
			return EARTH;
		}
		if (m.contains("wood") || m.contains("bamboo") || m.contains("ladder") || m.contains("scaffolding")
			|| m.contains("chiseled_bookshelf") || m.contains("mangrove")) {
			return WOOD;
		}
		if (m.contains("stone") || m.contains("deepslate") || m.contains("tuff") || m.contains("calcite") || m.contains("basalt")
			|| m.contains("brick") || m.contains("metal") || m.contains("anvil") || m.contains("chain") || m.contains("copper")
			|| m.contains("netherite") || m.contains("iron") || m.contains("glass") || m.contains("amethyst")
			|| m.contains("dripstone") || m.contains("lodestone") || m.contains("ancient_debris") || m.contains("bone_block")
			|| m.contains("trial_spawner") || m.contains("vault") || m.contains("heavy_core") || m.contains("lantern")) {
			return HARD;
		}
		return NORMAL;
	}
}
