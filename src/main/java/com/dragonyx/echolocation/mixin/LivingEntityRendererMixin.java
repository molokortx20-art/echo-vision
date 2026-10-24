package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.EchoMod;
import com.dragonyx.echolocation.render.EchoEntityLayers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Мобы в эхо-зрении: пока до них дошла волна, тело рисуется своей текстурой в оттенках серого
 * с линиями деталей и гаснет вместе с эхом — так же, как блоки, без светящейся обводки.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Shadow
	public abstract Identifier getTexture(LivingEntityRenderState state);

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;"))
	private RenderLayer echolocation$layer(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
										   boolean showBody, boolean translucent, boolean showOutline, Operation<RenderLayer> original) {
		RenderLayer layer = original.call(renderer, state, showBody, translucent, showOutline);
		if (layer == null || !EchoEntityLayers.active(state)) return layer;
		return EchoEntityLayers.of(this.getTexture(state));
	}

	@ModifyArg(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"),
		index = 6)
	private int echolocation$color(int color, @Local(argsOnly = true) LivingEntityRenderState state) {
		return EchoEntityLayers.active(state) ? EchoEntityLayers.vertexColor(state) : color;
	}

	/** Броня, предметы в руках и прочие слои рисуются обычными слоями и во тьме были бы чёрными пятнами поверх моба. */
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Z"))
	private boolean echolocation$features(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, Operation<Boolean> original) {
		if (EchoMod.blindActive() && EchoEntityLayers.active(state)) return false;
		return original.call(renderer, state);
	}
}
