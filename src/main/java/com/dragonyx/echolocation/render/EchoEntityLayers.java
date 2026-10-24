package com.dragonyx.echolocation.render;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.config.EchoConfig;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Эхо-зрение для существ: тело моба рисуется своей текстурой через шейдер echo_entity
 * (оттенки серого, линии деталей, яркость эха в цвете вершин) — так же, как блоки.
 */
public final class EchoEntityLayers {
	/** Ниже этой яркости существо снова рисуется обычным (во тьме — чёрным) слоем. */
	private static final float MIN_LEVEL = 0.02f;

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
			.withLocation(Identifier.of(EchoMod.MOD_ID, "pipeline/echo_entity"))
			.withVertexShader(Identifier.of(EchoMod.MOD_ID, "core/echo_entity"))
			.withFragmentShader(Identifier.of(EchoMod.MOD_ID, "core/echo_entity"))
			.withSampler("Sampler0")
			.withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
			.withoutBlend()
			.withCull(false)
			.build());

	private static final Function<Identifier, RenderLayer> LAYERS = Util.memoize(texture ->
		RenderLayer.of("echolocation_entity", RenderSetup.builder(PIPELINE)
			.texture("Sampler0", texture)
			.expectedBufferSize(1536)
			.build()));

	private EchoEntityLayers() {}

	public static void init() {
		// Загружает класс заранее, чтобы пайплайн попал в общий список до загрузки шейдеров.
	}

	public static RenderLayer of(Identifier texture) {
		return LAYERS.apply(texture);
	}

	/** Рисовать ли существо в эхо-зрении прямо сейчас. */
	public static boolean active(EntityRenderState state) {
		EchoConfig cfg = EchoConfig.get();
		return cfg.echoEnabled && cfg.style == EchoConfig.Style.ECHO_VISION
			&& ((EchoEntityState) state).echolocation$getLevel() > MIN_LEVEL;
	}

	/** Цвет вершин: оттенок эха в RGB, яркость / 2 в альфе (шейдер умножает обратно). */
	public static int vertexColor(EntityRenderState state) {
		EchoEntityState s = (EchoEntityState) state;
		int a = (int) (Math.min(1f, s.echolocation$getLevel() * 0.5f) * 255);
		return a << 24 | (s.echolocation$getColor() & 0xFFFFFF);
	}

	/** Обычное ли это существо для эхо-зрения (живое: у него есть текстура тела). */
	public static boolean visionStyle() {
		EchoConfig cfg = EchoConfig.get();
		return cfg.echoEnabled && cfg.style == EchoConfig.Style.ECHO_VISION;
	}
}
