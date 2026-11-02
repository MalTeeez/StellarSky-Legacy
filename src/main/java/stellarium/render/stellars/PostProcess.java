package stellarium.render.stellars;


import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import net.minecraft.client.renderer.OpenGlHelper;
import stellarapi.api.optics.EyeDetector;
import stellarium.StellarSkyResources;
import stellarium.render.shader.IShaderObject;
import stellarium.render.shader.IUniformField;
import stellarium.render.shader.ShaderHelper;
import stellarium.render.sky.SkyRenderInformation;
import stellarium.render.util.FramebufferCustom;
import stellarium.util.OpenGlUtil;
import stellarium.view.ViewerInfo;

/**
 * HDR post-processing chain: horizontal/vertical "scope" blur, an exposure query read back
 * from the frame, HDR->LDR tonemapping and linear->sRGB.
 *
 * Ported from 1.12's {@code stellarium.render.stellars.PostProcess}. The input frame is
 * {@code frame1}; {@link #preProcess()} binds it, and {@link #postProcess} resolves it to
 * whatever framebuffer was bound before.
 *
 * {@code GlStateManager.glGetInteger(...)} became {@code GL11.glGetInteger(...)}: 1.7.10 has no
 * GlStateManager, and raw GL is what Angelica's redirector tracks.
 */
public class PostProcess {
	private FramebufferCustom frame1 = null, frame2 = null;
	private int prevFramebufferBound;

	private IShaderObject scope, hdrToldr, linearToSRGB;
	private IUniformField fieldBrMult, fieldResDir, fieldBrScale;


	/**
	 * Exposure. 1.12 queries the frame on the GPU (mipmapped RGBE target pulled out with
	 * glGetTexImage through pixel-pack buffers) to adapt to what is actually on screen. That
	 * read back as a constant zero on both drivers tried here - direct glReadPixels off a
	 * 64x64 RGBA32F target included - while reporting no GL error, so this scales off the
	 * sun instead, which is the dominant term for a sky and needs no readback.
	 *
	 * ponytail: analytic exposure, revisit with an on-GPU average if a scene ever needs to
	 * adapt to something other than the sun.
	 */
	/**
	 * Exposure divisor. The frame-mean query cannot drive this: at night the mean is almost
	 * entirely star pixels, so measuring it damps the very thing that should be visible. The
	 * sun's elevation gives a clean anchor at both ends instead.
	 *
	 * Anchors, from measured values: a magnitude-0 star carries HDR ~0.25, so the night divisor
	 * has to sit well below 1 for stars to read; midday was verified correct at ~4800.
	 */
	// Measured star HDsR spans 1e-4 (mag 8) .. 1e-2 (mag 3), so the night divisor has to sit
	// near the bottom of that range for a starfield to read at all. A moonless night to noon is
	// ~1e6 in reality, so this span is not unreasonable.
	// Modern clamps its luminance-derived scale to [1, 1000]. A floor below 1 divides the frame
	// by less than one, i.e. multiplies it, which blew the Milky Way out to white.
	private static final double NIGHT_EXPOSURE = 1.0;
	// Modern derives this from the frame's mean luminance and clamps to [1, 1000]; anchoring on
	// the sun instead needs a fixed pair, and 4800 sat above that ceiling - it was tuned while
	// the post chain was still being discarded by the alpha test, so nothing validated it.
	private static final double DAY_EXPOSURE = 500.0;

	private float sunHeight = 1.0f;

	private double brScale() {
		// Use the same sun height the atmosphere does. Deriving it from cos(celestialAngle)
		// disagreed with the atmosphere by about half the sky and dropped dusk onto the night
		// floor, which flattened it to a 10000x-overexposed blue.
		double sun = this.sunHeight;

		// Twilight window: the sky stays lit for a while after the sun is geometrically below
		// the horizon. Without this the curve falls straight to the night floor at sunset and
		// dusk renders as a flat, 10000x-overexposed blue.
		// Twilight sky brightness grows roughly exponentially with sun height, so ramp the exposure
		// log-linearly: night floor at -0.10 (~-6 deg), full day by +0.02. A day^4 ramp over a
		// wider window stayed near the floor until the sun was up, washing the horizon-crossing
		// out white (sun at 0 needs ~200, it gave ~14).
		double day = Math.max(0.0, Math.min(1.0, (sun + 0.10) / 0.12));
		double scale = NIGHT_EXPOSURE * Math.pow(DAY_EXPOSURE / NIGHT_EXPOSURE, day);

		return Math.max(1.0e-4, Math.min(1000000.0, scale * stellarium.stellars.OpticsHelper.getExposure()));
	}

	public void initialize() {
		this.setupShader();
	}

	public void onResize(int width, int height) {

		if (this.frame1 != null)
			frame1.deleteFramebuffer();
		if (this.frame2 != null)
			frame2.deleteFramebuffer();
		// HDR colour target. RGBA, not RGB: the atmosphere composite writes this frame
		// RGBE-encoded (the exponent travels in alpha, see atmosphere_refraction.psh and
		// scope.psh), and an RGB target silently drops that alpha, so every later decode
		// multiplies by exp2(127) and the whole sky saturates to a white haze.
		this.frame1 = FramebufferCustom.builder()
				.texFormat(OpenGlUtil.RGBA32F, GL11.GL_RGBA, OpenGlUtil.TEXTURE_FLOAT)
				.depthStencil(false, false)
				.build(width, height);

		// Float as well: the whole chain is linear now, so there is no RGBE encoding to fit
		// HDR into 8 bits. An 8-bit intermediate here quantised the faint end of the starfield
		// (and, with the composite already linear, mangled the sky outright).
		this.frame2 = FramebufferCustom.builder()
				.texFormat(OpenGlUtil.RGBA32F, GL11.GL_RGBA, OpenGlUtil.TEXTURE_FLOAT)
				.depthStencil(false, false)
				.build(width, height);

	}

	public void setupShader() {
		this.scope = ShaderHelper.getInstance().buildShader("Scope",
				StellarSkyResources.vertexScope,
				StellarSkyResources.fragmentScope);
		scope.getField("texture").setInteger(0);
		this.fieldBrMult = scope.getField("brightnessMult");
		this.fieldResDir = scope.getField("resDirection");

		this.hdrToldr = ShaderHelper.getInstance().buildShader("HDRtoLDR",
				StellarSkyResources.vertexHDRtoLDR,
				StellarSkyResources.fragmentHDRtoLDR);
		hdrToldr.getField("texture").setInteger(0);
		this.fieldBrScale = hdrToldr.getField("brScale");


		this.linearToSRGB = ShaderHelper.getInstance().buildShader("linearToSRGB",
				StellarSkyResources.vertexLinearToSRGB,
				StellarSkyResources.fragmentLinearToSRGB);
		linearToSRGB.getField("texture").setInteger(0);
	}

	public void preProcess() {
		this.prevFramebufferBound = GL11.glGetInteger(OpenGlUtil.FRAMEBUFFER_BINDING);

		frame1.bindFramebuffer(false);
		frame1.framebufferClear();
	}

	public void postProcess(SkyRenderInformation info) {
		long currentTime = System.currentTimeMillis();
		// Read the sun the atmosphere is lit by straight from the dimension: the provider is not
		// always ours (Patch_Provider off, or another mod swapping it after us), and vanilla's
		// celestial angle then put exposure on a different clock - day exposure over a twilight
		// sky at dawn (black), night exposure over a lit sky at dusk (white).
		stellarium.world.StellarDimensionManager dim = stellarium.world.StellarDimensionManager.get(info.world);
		this.sunHeight = dim != null && !dim.getSuns().isEmpty()
				? (float) stellarapi.api.lib.math.Spmath.sind(dim.getSuns().get(0).getCurrentHorizontalPos().y)
				: (float) Math.cos(2.0 * Math.PI * info.world.getCelestialAngle(info.partialTicks));
		// Every stage of this chain replaces its target, and the query shader writes only .r
		// (alpha 0), so blending must be off or the query writes nothing. Save the caller's
		// state and put it back: leaving depth testing off breaks the terrain drawn after us.
		boolean hadBlend = GL11.glIsEnabled(GL11.GL_BLEND);
		boolean hadDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		// 1.12 runs its post chain inside SkyRenderer's disableAlpha()/enableAlpha() pair.
		// Vanilla re-enables the alpha test after the sky pass here, and it would reject the
		// whole screen quad on the composite's alpha.
		boolean hadAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_ALPHA_TEST);

		ViewerInfo viewer = info.info;
		double multRed = viewer.colorMultiplier.getX();
		double multGreen = viewer.colorMultiplier.getY();
		double multBlue = viewer.colorMultiplier.getZ();

		// Blur on X-axis
		frame2.bindFramebuffer(false);
		frame2.framebufferClear();

		double resolution = Math.toRadians(EyeDetector.DEFAULT_RESOLUTION) / viewer.multiplyingPower;

		scope.bindShader();
		fieldBrMult.setDouble4(1.0, 1.0, 1.0, 1.0);
		fieldResDir.setDouble2(resolution / info.relativeWidth, 0.0);
		frame1.bindFramebufferTexture();
		frame1.renderFullQuad();
		scope.releaseShader();

		// Blur on Y-axis and light power
		frame1.bindFramebuffer(false);
		frame1.framebufferClear();

		scope.bindShader();
		fieldBrMult.setDouble4(multRed, multGreen, multBlue, 1.0);
		fieldResDir.setDouble2(0.0, resolution / info.relativeHeight);
		frame2.bindFramebufferTexture();
		frame2.renderFullQuad();
		scope.releaseShader();

		// HDR to LDR
		frame2.bindFramebuffer(true);
		frame2.framebufferClear();

		hdrToldr.bindShader();
		// The uniform is only settable while the program is bound, and it must be set every
		// frame - it was being computed and then never uploaded, leaving the divisor at 0.
		fieldBrScale.setDouble(this.brScale());
		frame1.bindFramebufferTexture();
		frame1.renderFullQuad();
		hdrToldr.releaseShader();

		// Linear RGB to sRGB
		OpenGlUtil.bindFramebuffer(OpenGlUtil.FRAMEBUFFER_GL, this.prevFramebufferBound);

		linearToSRGB.bindShader();
		frame2.bindFramebufferTexture();
		frame2.renderFullQuad();
		linearToSRGB.releaseShader();

		if (hadAlphaTest)
			GL11.glEnable(GL11.GL_ALPHA_TEST);
		if (hadDepth)
			GL11.glEnable(GL11.GL_DEPTH_TEST);
		if (hadBlend)
			GL11.glEnable(GL11.GL_BLEND);
	}
}
