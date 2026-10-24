package com.dragonyx.echolocation.compat;

import com.dragonyx.echolocation.echo.VoiceEchoes;
import su.plo.slib.api.position.Pos3d;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.audio.source.AudioSourceWriteEvent;
import su.plo.voice.api.client.event.connection.UdpClientPacketSendEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.proto.data.audio.source.EntitySourceInfo;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.proto.data.audio.source.StaticSourceInfo;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

/**
 * Клиентский аддон Plasmo Voice (загружается, только если Plasmo Voice установлен).
 * <p>
 * Свой голос: громкость берётся из обработанного кадра микрофона, а волна идёт только когда
 * Plasmo действительно отправляет голосовой пакет — то есть ты в эфире (активация голосом или по кнопке).
 * Чужие голоса: волна из точки источника — от игрока, существа или статичной точки в мире.
 */
@Addon(
	id = "pv-addon-echovision",
	name = "Echo Vision",
	scope = AddonLoaderScope.CLIENT,
	version = "1.0.2",
	authors = {"dragonyx"}
)
public final class PlasmoVoiceAddon {
	/** Шёпот в Plasmo — это активация с маленькой дистанцией. */
	private static final int WHISPER_DISTANCE = 8;

	private volatile short[] lastMicFrame;

	public static void register() {
		PlasmoVoiceClient.getAddonsLoader().load(new PlasmoVoiceAddon());
	}

	@EventSubscribe
	public void onCapture(AudioCaptureProcessedEvent event) {
		short[] mono = event.getProcessed() != null ? event.getProcessed().getMono() : null;
		lastMicFrame = mono != null ? mono : event.getRawSamples();
	}

	@EventSubscribe
	public void onSend(UdpClientPacketSendEvent event) {
		if (!(event.getPacket() instanceof PlayerAudioPacket packet)) return;
		short[] frame = lastMicFrame;
		if (frame != null) VoiceEchoes.ownVoice(frame, packet.getDistance() <= WHISPER_DISTANCE);
	}

	@EventSubscribe
	public void onSourceWrite(AudioSourceWriteEvent event) {
		SourceInfo info = event.getSource().getSourceInfo();
		short[] samples = event.getSamples();
		if (info instanceof PlayerSourceInfo player) {
			VoiceEchoes.entityVoice(player.getPlayerInfo().getPlayerId(), samples, false);
		} else if (info instanceof EntitySourceInfo entity) {
			VoiceEchoes.entityIdVoice(info.getId(), entity.getEntityId(), samples);
		} else if (info instanceof StaticSourceInfo stat) {
			Pos3d p = stat.getPosition();
			VoiceEchoes.locatedVoice(info.getId(), p.getX(), p.getY(), p.getZ(), samples);
		}
		// DirectSourceInfo — звук «в ушах» без места в мире (рация, музыка аддонов): эха нет.
	}
}
