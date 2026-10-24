package com.dragonyx.echolocation.compat;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.echo.VoiceEchoes;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Плагин для Simple Voice Chat (загружается, только если он установлен).
 * Свой микрофон и голоса других игроков рождают волну эха, как любой другой звук.
 * События приходят из аудиопотоков, поэтому здесь только громкость — дальше работает {@link VoiceEchoes}.
 */
public final class EchoVoicePlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return EchoMod.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(ClientSoundEvent.class, e ->
			VoiceEchoes.ownVoice(e.getRawAudio(), e.isWhispering()));
		registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, e ->
			VoiceEchoes.entityVoice(e.getEntityId(), e.getRawAudio(), e.isWhispering()));
		registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, e -> {
			Position p = e.getPosition();
			VoiceEchoes.locatedVoice(e.getId(), p.getX(), p.getY(), p.getZ(), e.getRawAudio());
		});
	}
}
