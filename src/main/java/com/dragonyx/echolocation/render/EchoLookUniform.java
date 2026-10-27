package com.dragonyx.echolocation.render;

import com.dragonyx.echolocation.config.EchoConfig;
import net.minecraft.client.render.TextureTransform;
import org.joml.Matrix4f;

/**
 * Параметры вида для шейдеров эха. Матрица текстуры в шейдерах эха не нужна, а игра сама отправляет её
 * при каждой отрисовке слоя — в ней и передаём настройки, поэтому любое изменение видно сразу, без
 * перезагрузки шейдеров.
 * <p>
 * Столбцы (TextureMat[i] в шейдере):
 * [0] — цвет линий RGB и сколько видно текстуру;
 * [1] — заливка, контуры углов, линии деталей, затемнение вдали;
 * [2] — толщина контуров;
 * [3] — цвет эха RGB (для существ и предметов).
 */
public final class EchoLookUniform {
	public static final TextureTransform TRANSFORM = new TextureTransform("echovision_look", EchoLookUniform::matrix);

	private EchoLookUniform() {}

	private static Matrix4f matrix() {
		EchoConfig c = EchoConfig.get();
		int l = c.lineColor, e = c.echoColor;
		return new Matrix4f(
			r(l), g(l), b(l), c.textureAmount,
			c.fillAmount, c.edgeGlow, c.detailLines, c.distanceFade,
			c.edgeWidth, 0, 0, 0,
			r(e), g(e), b(e), 1);
	}

	private static float r(int rgb) {
		return ((rgb >> 16) & 0xFF) / 255f;
	}

	private static float g(int rgb) {
		return ((rgb >> 8) & 0xFF) / 255f;
	}

	private static float b(int rgb) {
		return (rgb & 0xFF) / 255f;
	}
}
