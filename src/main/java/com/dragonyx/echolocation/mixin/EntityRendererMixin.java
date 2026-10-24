package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoField;
import com.dragonyx.echolocation.render.EchoEntityLayers;
import com.dragonyx.echolocation.render.EchoEntityState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Эхо существ: здесь в состояние отрисовки передаётся яркость эха. В эхо-зрении по ней сущность
 * рисуется своей текстурой (EchoEntityLayers), в остальных стилях — светящейся обводкой.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "updateRenderState", at = @At("TAIL"))
	private void echolocation$state(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
		float level = echolocation$level(entity) * EchoConfig.get().brightness;
		int color = level > 0 ? EchoField.INSTANCE.entityColor(entity.getId()) : 0;
		((EchoEntityState) state).echolocation$set(level, color);
	}

	@ModifyExpressionValue(method = "updateRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/MinecraftClient;hasOutline(Lnet/minecraft/entity/Entity;)Z"))
	private boolean echolocation$outline(boolean original, @Local(argsOnly = true) Entity entity) {
		return original || echolocation$outlined(entity) && echolocation$level(entity) > 0.03f;
	}

	@ModifyExpressionValue(method = "updateRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/entity/Entity;getTeamColorValue()I"))
	private int echolocation$color(int original, @Local(argsOnly = true) Entity entity) {
		if (!echolocation$outlined(entity)) return original;
		float level = echolocation$level(entity);
		if (level <= 0.03f) return original;
		int c = EchoField.INSTANCE.entityColor(entity.getId());
		float k = Math.min(1f, level * EchoConfig.get().brightness);
		int r = (int) (((c >> 16) & 0xFF) * k);
		int g = (int) (((c >> 8) & 0xFF) * k);
		int b = (int) ((c & 0xFF) * k);
		return r << 16 | g << 8 | b;
	}

	/** Обводка — только в стилях без эхо-зрения: в нём все сущности рисуются сами (EchoEntityLayers). */
	private static boolean echolocation$outlined(Entity entity) {
		return !EchoEntityLayers.visionStyle();
	}

	private static float echolocation$level(Entity entity) {
		EchoConfig cfg = EchoConfig.get();
		if (!cfg.echoEnabled || !cfg.showEntities) return 0f;
		return EchoField.INSTANCE.entityLevel(entity.getId());
	}
}
