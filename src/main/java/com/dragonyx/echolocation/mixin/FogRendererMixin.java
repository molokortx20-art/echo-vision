package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.config.EchoConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Слепота: чёрный туман вплотную к глазам. Всё, что не подсвечено эхом, тонет во тьме.
 * <p>
 * Меняем уже посчитанные FogData и цвет, а не отменяем метод: Sodium читает эти же
 * локальные переменные для своего тумана чанков, иначе блоки остались бы видны.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/fog/FogData;renderDistanceEnd:F", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void echolocation$blind(CallbackInfoReturnable<Vector4f> cir, @Local FogData data, @Local Vector4f color) {
		if (!EchoMod.blindActive()) return;
		float radius = EchoConfig.get().visionRadius;
		float end = Math.max(0.05f, radius);
		float start = radius * 0.2f;
		color.set(0f, 0f, 0f, 1f);
		data.environmentalStart = start;
		data.environmentalEnd = end;
		data.renderDistanceStart = Math.min(data.renderDistanceStart, start);
		data.renderDistanceEnd = Math.min(data.renderDistanceEnd, end);
		data.skyEnd = 0f;
		data.cloudEnd = 0f;
	}
}
