package com.dragonyx.echolocation.screen;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoClock;
import com.dragonyx.echolocation.echo.EchoEngine;

/**
 * Живой предпросмотр на экранах настроек: пока они открыты, мир виден справа от панели, и раз
 * в пару секунд раздаётся беззвучный щелчок — эхо всё время на экране. Изменили вид — щелчок сразу.
 */
final class EchoPreview {
	private static final double INTERVAL = 2.2;
	/** После изменения ползунка ждём, пока его перестанут тянуть. */
	private static final double DEBOUNCE = 0.25;

	private static double next;
	private static double pending = -1;

	private EchoPreview() {}

	static void tick() {
		if (!EchoConfig.get().previewPulses) return;
		double now = EchoClock.now();
		if (pending > 0 && now >= pending || pending <= 0 && now >= next) {
			EchoEngine.INSTANCE.previewPulse();
			pending = -1;
			next = now + INTERVAL;
		}
	}

	/** Вид изменился — показать его новым щелчком. */
	static void changed() {
		if (EchoConfig.get().previewPulses) pending = EchoClock.now() + DEBOUNCE;
	}

	/** Щелчок прямо сейчас (кнопка на панели). */
	static void pulseNow() {
		EchoEngine.INSTANCE.previewPulse();
		next = EchoClock.now() + INTERVAL;
	}
}
