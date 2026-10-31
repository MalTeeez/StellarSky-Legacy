package stellarium.render.stellars;

import stellarium.render.sky.SkyRenderInformation;

/**
 * Owner of the stellar render chain, mirroring 1.12's {@code StellarRenderer}. For now it holds
 * the HDR post-processing chain that wraps the existing FSM-driven star/atmosphere render.
 */
public enum StellarRenderer {
	INSTANCE;

	private final PostProcess postProcessor = new PostProcess();
	private int prevWidth = 0, prevHeight = 0;

	public void initialize() {
		postProcessor.initialize();
		this.prevWidth = 0;
		this.prevHeight = 0;
	}

	public void preRender(SkyRenderInformation info) {
		if (info.minecraft.displayWidth != this.prevWidth || info.minecraft.displayHeight != this.prevHeight) {
			postProcessor.onResize(info.minecraft.displayWidth, info.minecraft.displayHeight);
			this.prevWidth = info.minecraft.displayWidth;
			this.prevHeight = info.minecraft.displayHeight;
		}

		postProcessor.preProcess();
	}

	public void postProcess(SkyRenderInformation info) {
		postProcessor.postProcess(info);
	}
}
