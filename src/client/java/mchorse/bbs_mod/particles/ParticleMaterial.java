package mchorse.bbs_mod.particles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

public enum ParticleMaterial
{
	OPAQUE("particles_opaque"), ALPHA("particles_alpha"), BLEND("particles_blend"), ADDITIVE("particles_add");

	public final String id;

	public static ParticleMaterial fromString(String material)
	{
		for (ParticleMaterial mat : values())
		{
			if (mat.id.equals(material))
			{
				return mat;
			}
		}

		return OPAQUE;
	}

	private ParticleMaterial(String id)
	{
		this.id = id;
	}

	public void beginRender()
	{
		switch (this)
		{
			case OPAQUE:
			case ALPHA:
				RenderSystem.disableBlend();
				break;
			case BLEND:
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				break;
			case ADDITIVE:
				RenderSystem.enableBlend();
				RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
				break;
		}
	}

	public void endRender()
	{
		RenderSystem.disableBlend();
	}
}