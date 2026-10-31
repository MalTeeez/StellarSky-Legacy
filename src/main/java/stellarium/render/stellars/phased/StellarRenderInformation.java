package stellarium.render.stellars.phased;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import stellarium.render.shader.IShaderObject;
import stellarium.render.sky.SkyRenderInformation;
import stellarium.render.stellars.access.IDominateRenderer;
import stellarium.view.ViewerInfo;

public class StellarRenderInformation {
	public final Minecraft minecraft;
	public final WorldClient world;
	public final float partialTicks;
	public final boolean isFrameBufferEnabled;
	public final float deepDepth;

	public final ViewerInfo info;
	public final double screenSize;
	public final double relativeWidth, relativeHeight;

	/** Refraction offset at the view boundary, set by the atmosphere in Prepare. */
	public double boundaryRefraction;

	public StellarRenderInformation(SkyRenderInformation info) {
		this.minecraft = info.minecraft;
		this.world = info.world;
		this.partialTicks = info.partialTicks;
		this.isFrameBufferEnabled = info.isFrameBufferEnabled;
		this.deepDepth = info.deepDepth;
		
		this.info = info.info;
		this.screenSize = info.screenSize;

		this.relativeWidth = info.relativeWidth;
		this.relativeHeight = info.relativeHeight;
	}
	
	private IShaderObject activeShader;
	private IDominateRenderer dominater;
	
	public void setActiveShader(IShaderObject activeShader) {
		this.activeShader = activeShader;
	}
		
	public IShaderObject getActiveShader() {
		return this.activeShader;
	}
	
	public IDominateRenderer getDominateRenderer() {
		return this.dominater;
	}

	public void setDominateRenderer(IDominateRenderer dominater) {
		this.dominater = dominater;
	}
}
