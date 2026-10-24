package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.config.EchoConfig;
import com.dragonyx.echolocation.echo.EchoField;
import com.dragonyx.echolocation.render.EchoEntityLayers;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рука и предмет в руке от первого лица видны в эхе твоих же звуков. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At("HEAD"))
	private void echolocation$begin(float tickProgress, net.minecraft.client.util.math.MatrixStack matrices,
									net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
									ClientPlayerEntity player, int light, CallbackInfo ci,
									@Share("previous") LocalFloatRef previous) {
		float level = EchoConfig.get().showEntities ? EchoField.INSTANCE.entityLevel(player.getId()) * EchoConfig.get().brightness : 0f;
		previous.set(EchoEntityLayers.begin(level));
	}

	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
		at = @At("RETURN"))
	private void echolocation$end(CallbackInfo ci, @Share("previous") LocalFloatRef previous) {
		EchoEntityLayers.end(previous.get());
	}
}
