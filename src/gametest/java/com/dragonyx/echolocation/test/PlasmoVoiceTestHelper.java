package com.dragonyx.echolocation.test;

import com.dragonyx.echolocation.compat.PlasmoVoiceAddon;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.socket.UdpClient;

import java.lang.reflect.Proxy;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.connection.UdpClientPacketSendEvent;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.UUID;

/** Отдельный класс, чтобы тест не трогал классы Plasmo Voice, когда его нет. */
final class PlasmoVoiceTestHelper {
	private PlasmoVoiceTestHelper() {}

	/** Как при передаче в эфир: кадр микрофона, затем отправка голосового пакета — из «аудиопотока». */
	static void speak(short[] frame) throws InterruptedException {
		PlasmoVoiceAddon addon = new PlasmoVoiceAddon();
		AudioCaptureProcessedEvent.ProcessedSamples processed = new AudioCaptureProcessedEvent.ProcessedSamples() {
			@Override
			public short[] getMono() {
				return frame;
			}

			@Override
			public short[] getStereo() {
				return frame;
			}
		};
		Thread audio = new Thread(() -> {
			addon.onCapture(new AudioCaptureProcessedEvent(stub(AudioCapture.class), stub(InputDevice.class), frame, processed));
			addon.onSend(new UdpClientPacketSendEvent(stub(UdpClient.class),
				new PlayerAudioPacket(1L, new byte[]{1, 2, 3},UUID.randomUUID(), (short) 16, false)));
		}, "Fake Plasmo Voice audio");
		audio.start();
		audio.join();
	}

	/** Пустая реализация интерфейса Plasmo: события требуют ненулевые объекты, но аддон их не трогает. */
	private static <T> T stub(Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null));
	}
}
