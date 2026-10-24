package com.dragonyx.echolocation.render;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.mixin.RenderLayerAccessor;
import com.dragonyx.echolocation.mixin.RenderSetupAccessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;

import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Эхо-зрение для всего, что рисуется моделью с текстурой: мобы вместе с бронёй и предметами в руках,
 * неживые сущности (лодки, рамки, выпавшие предметы), блок-сущности (сундуки, таблички, кровати)
 * и рука игрока. Пока объект освещён эхом ({@link #begin}), каждый его текстурный слой в очереди
 * отрисовки подменяется слоем с той же текстурой и шейдером echo_entity.
 * <p>
 * Яркость нельзя передать через цвет вершин — его задают сами рендеры (броня, предметы),
 * поэтому она зашита в пайплайн: 16 ступеней от тусклого до вспышки фронта.
 */
public final class EchoEntityLayers {
	/** Ниже этой яркости объект рисуется обычным (во тьме — чёрным) слоем. */
	private static final float MIN_LEVEL = 0.02f;
	private static final int STEPS = 16;
	/** Наибольшая яркость ступени (со вспышкой фронта). */
	private static final float MAX_LEVEL = 2.0f;

	private static final RenderPipeline[] PIPELINES = new RenderPipeline[STEPS];

	static {
		for (int i = 0; i < STEPS; i++) {
			PIPELINES[i] = RenderPipelines.register(
				RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
					.withLocation(Identifier.of(EchoMod.MOD_ID, "pipeline/echo_entity_" + i))
					.withVertexShader(Identifier.of(EchoMod.MOD_ID, "core/echo_entity"))
					.withFragmentShader(Identifier.of(EchoMod.MOD_ID, "core/echo_entity"))
					.withShaderDefine("ECHO_LEVEL", levelOf(i))
					.withSampler("Sampler0")
					.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
					.withoutBlend()
					.withCull(false)
					.build());
		}
	}

	private static final Map<Identifier, RenderLayer[]> LAYERS = new HashMap<>();
	/** Текстура каждого встреченного слоя (или «нет» — такой слой не подменяем). */
	private static final Map<RenderLayer, Optional<Identifier>> TEXTURES = new IdentityHashMap<>();

	/** Яркость эха объекта, который рисуется прямо сейчас (0 — не освещён). Только поток отрисовки. */
	private static float current;

	private EchoEntityLayers() {}

	public static void init() {
		// Загружает класс заранее, чтобы пайплайны попали в общий список до загрузки шейдеров.
	}

	private static float levelOf(int step) {
		return MAX_LEVEL * (step + 1) / STEPS;
	}

	/** Эхо-зрение включено. */
	public static boolean visionStyle() {
		EchoConfig cfg = EchoConfig.get();
		return cfg.echoEnabled && cfg.style == EchoConfig.Style.ECHO_VISION;
	}

	/** Начало отрисовки объекта с яркостью эха {@code level}; возвращает прежнее значение для {@link #end}. */
	public static float begin(float level) {
		float previous = current;
		current = level;
		return previous;
	}

	public static void end(float previous) {
		current = previous;
	}

	public static float levelOf(EntityRenderState state) {
		return ((EchoEntityState) state).echolocation$getLevel();
	}

	/** Подмена слоя в очереди отрисовки, если сейчас рисуется освещённый эхом объект. */
	public static RenderLayer swap(RenderLayer layer) {
		float level = current;
		// Без слепоты объекты видны как обычно — подмена нужна только во тьме.
		if (level <= MIN_LEVEL || layer == null || !visionStyle() || !EchoMod.blindActive()) return layer;
		Identifier texture = textureOf(layer);
		if (texture == null) return layer;
		int step = Math.max(0, Math.min(STEPS - 1, Math.round(level / MAX_LEVEL * STEPS) - 1));
		RenderLayer[] layers = LAYERS.computeIfAbsent(texture, t -> new RenderLayer[STEPS]);
		RenderLayer echo = layers[step];
		if (echo == null) {
			echo = layers[step] = RenderLayer.of("echovision_entity_" + step, RenderSetup.builder(PIPELINES[step])
				.texture("Sampler0", texture)
				.expectedBufferSize(1536)
				.build());
		}
		return echo;
	}

	/**
	 * Текстура слоя, если его стоит подменять: формат вершин сущностей, не обводка,
	 * не тень, не блеск зачарования и не наш собственный слой.
	 */
	private static Identifier textureOf(RenderLayer layer) {
		return TEXTURES.computeIfAbsent(layer, l -> {
			if (l.isOutline() || l.getVertexFormat() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) return Optional.empty();
			for (RenderPipeline p : PIPELINES) if (l.getRenderPipeline() == p) return Optional.empty();
			RenderSetup setup = ((RenderLayerAccessor) l).echolocation$getRenderSetup();
			Object spec = ((RenderSetupAccessor) (Object) setup).echolocation$getTextures().get("Sampler0");
			Identifier id = locationOf(spec);
			if (id == null) return Optional.empty();
			String path = id.getPath();
			if (path.contains("shadow") || path.contains("glint") || path.contains("enchanted")) return Optional.empty();
			return Optional.of(id);
		}).orElse(null);
	}

	/** RenderSetup.TextureSpec — закрытая запись; её первое поле — Identifier текстуры. */
	private static Identifier locationOf(Object spec) {
		if (spec == null || !spec.getClass().isRecord()) return null;
		try {
			for (RecordComponent c : spec.getClass().getRecordComponents()) {
				if (c.getType() == Identifier.class) {
					var accessor = c.getAccessor();
					accessor.setAccessible(true);
					return (Identifier) accessor.invoke(spec);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			EchoMod.LOGGER.debug("Cannot read texture of {}", spec, e);
		}
		return null;
	}
}
