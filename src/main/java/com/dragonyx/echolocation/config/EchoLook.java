package com.dragonyx.echolocation.config;

import java.util.Objects;

/**
 * Внешний вид эха — то, что сохраняется в пресет: цвета, заливка, текстура, линии, кольцо фронта.
 * Звук, дальность и источники в пресет не входят.
 */
public final class EchoLook {
	public EchoConfig.ColorMode colorMode = EchoConfig.ColorMode.SINGLE;
	public int echoColor = 0xFFFFFF;
	public int lineColor = 0xFFFFFF;
	public float brightness = 1f;
	public float echoDuration = 3f;
	public float textureAmount = 1f;
	public float fillAmount = 0.5f;
	public float edgeGlow = 0f;
	public float edgeWidth = 0.04f;
	public float detailLines = 1f;
	public float distanceFade = 0f;
	public float waveNoise = 0f;
	public boolean wavefrontRing = true;
	public float ringWidth = 0.4f;
	public boolean wavefrontFlash = true;

	public static EchoLook of(EchoConfig c) {
		EchoLook l = new EchoLook();
		l.colorMode = c.colorMode;
		l.echoColor = c.echoColor;
		l.lineColor = c.lineColor;
		l.brightness = c.brightness;
		l.echoDuration = c.echoDuration;
		l.textureAmount = c.textureAmount;
		l.fillAmount = c.fillAmount;
		l.edgeGlow = c.edgeGlow;
		l.edgeWidth = c.edgeWidth;
		l.detailLines = c.detailLines;
		l.distanceFade = c.distanceFade;
		l.waveNoise = c.waveNoise;
		l.wavefrontRing = c.wavefrontRing;
		l.ringWidth = c.ringWidth;
		l.wavefrontFlash = c.wavefrontFlash;
		return l;
	}

	public void applyTo(EchoConfig c) {
		c.colorMode = colorMode == null ? EchoConfig.ColorMode.SINGLE : colorMode;
		c.echoColor = echoColor & 0xFFFFFF;
		c.lineColor = lineColor & 0xFFFFFF;
		c.brightness = brightness;
		c.echoDuration = echoDuration;
		c.textureAmount = textureAmount;
		c.fillAmount = fillAmount;
		c.edgeGlow = edgeGlow;
		c.edgeWidth = edgeWidth;
		c.detailLines = detailLines;
		c.distanceFade = distanceFade;
		c.waveNoise = waveNoise;
		c.wavefrontRing = wavefrontRing;
		c.ringWidth = ringWidth;
		c.wavefrontFlash = wavefrontFlash;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof EchoLook l)) return false;
		return colorMode == l.colorMode && echoColor == l.echoColor && lineColor == l.lineColor
			&& near(brightness, l.brightness) && near(echoDuration, l.echoDuration) && near(textureAmount, l.textureAmount)
			&& near(fillAmount, l.fillAmount) && near(edgeGlow, l.edgeGlow) && near(edgeWidth, l.edgeWidth)
			&& near(detailLines, l.detailLines) && near(distanceFade, l.distanceFade) && near(waveNoise, l.waveNoise)
			&& wavefrontRing == l.wavefrontRing && near(ringWidth, l.ringWidth) && wavefrontFlash == l.wavefrontFlash;
	}

	@Override
	public int hashCode() {
		return Objects.hash(colorMode, echoColor, lineColor, wavefrontRing, wavefrontFlash);
	}

	/** Ползунки шагают дискретно, а в файле числа с плавающей точкой — сравниваем с допуском. */
	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.004f;
	}
}
