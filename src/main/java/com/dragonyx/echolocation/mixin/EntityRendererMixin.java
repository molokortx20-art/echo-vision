package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoField;
import com.dragonyx.echolocation.render.EchoEntityState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Эхо существ: здесь в состояние отрисовки передаётся яркость эха, по ней сущность рисуется
 * своей текстурой (EchoEntityLayers). Обводки свечения нет: она видна сквозь стены.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void echolocation$state(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
		float level = echolocation$level(entity) * EchoConfig.get().brightness;
		int color = level > 0 ? EchoField.INSTANCE.entityColor(entity.getId()) : 0;
		((EchoEntityState) state).echolocation$set(level, color);
	}

	private static float echolocation$level(Entity entity) {
		EchoConfig cfg = EchoConfig.get();
		if (!cfg.echoEnabled || !cfg.showEntities) return 0f;
		return EchoField.INSTANCE.entityLevel(entity.getId());
	}
}
