package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.EchoMod;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** При слепоте небо, солнце и звёзды не рисуются — как у ванильного эффекта слепоты. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Inject(method = "hasBlindnessOrDarkness", at = @At("HEAD"), cancellable = true)
	private void echolocation$noSky(Camera camera, CallbackInfoReturnable<Boolean> cir) {
		if (EchoMod.blindActive()) cir.setReturnValue(true);
	}
}
