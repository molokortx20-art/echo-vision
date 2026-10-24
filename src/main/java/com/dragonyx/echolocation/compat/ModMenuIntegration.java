package com.dragonyx.echolocation.compat;

import com.dragonyx.echolocation.screen.EchoConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Кнопка настроек в списке модов Mod Menu (если он установлен). */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return EchoConfigScreen::new;
	}
}
