package com.dragonyx.echolocation.mixin;

import com.dragonyx.echolocation.render.EchoEntityState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EchoEntityState {
	@Unique
	private float echolocation$level;
	@Unique
	private int echolocation$color;

	@Override
	public float echolocation$getLevel() {
		return echolocation$level;
	}

	@Override
	public int echolocation$getColor() {
		return echolocation$color;
	}

	@Override
	public void echolocation$set(float level, int color) {
		echolocation$level = level;
		echolocation$color = color;
	}
}
