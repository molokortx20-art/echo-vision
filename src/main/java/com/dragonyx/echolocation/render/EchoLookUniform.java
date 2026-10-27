package com.dragonyx.echolocation.render;

import com.dragonyx.echolocation.config.EchoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TextureTransform;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Параметры вида для шейдеров эха. Матрица текстуры в шейдерах эха не нужна, а игра сама отправляет её
 * при каждой отрисовке слоя — в ней и передаём настройки, поэтому любое изменение видно сразу, без
 * перезагрузки шейдеров.
 * <p>
 * Столбцы (TextureMat[i] в шейдере):
 * [0] — цвет линий RGB и сколько видно текстуру;
 * [1] — заливка, пятна, линии деталей, затемнение вдали;
 * [2] — (не занято) и позиция камеры по модулю 1024 — чтобы пятна лежали на мире, а не плыли за камерой;
 * [3] — цвет эха RGB (для существ и предметов).
 */
public final class EchoLookUniform {
	public static final TextureTransform TRANSFORM = new TextureTransform("echovision_look", EchoLookUniform::matrix);

	private EchoLookUniform() {}

	private static Matrix4f matrix() {
		EchoConfig c = EchoConfig.get();
		int l = c.lineColor, e = c.echoColor;
		Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
		return new Matrix4f(
			r(l), g(l), b(l), c.textureAmount,
			c.fillAmount, c.mottle, c.detailLines, c.distanceFade,
			0, wrap(cam.x), wrap(cam.y), wrap(cam.z),
			r(e), g(e), b(e), 1);
	}

	/** Координата по модулю 1024: во float большие координаты теряют точность. */
	private static float wrap(double v) {
		return (float) (v - Math.floor(v / 1024.0) * 1024.0);
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
