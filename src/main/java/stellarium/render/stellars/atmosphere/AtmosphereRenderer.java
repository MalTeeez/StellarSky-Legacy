package stellarium.render.stellars.atmosphere;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizon.gtnhlib.client.opengl.GLCaps;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Vector3;
import stellarium.lib.render.IGenericRenderer;
import stellarium.render.shader.ShaderHelper;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.phased.StellarRenderInformation;
import stellarium.render.util.FramebufferCustom;
import stellarium.stellars.util.ExtinctionRefraction;
import stellarium.util.OpenGlUtil;
import stellarium.util.math.Allocator;

/**
 * Atmosphere renderer, following 1.12's structure: the whole sky is rendered into a full
 * resolution HDR framebuffer in view space, extinguished, then composited to the screen
 * through the atmospheric refraction pass.
 *
 * Deviations from 1.12, all because 1.7.10 lacks the API:
 * - no {@code GlStateManager}, and no {@code BufferBuilder}/{@code VertexDirect}: the sphere
 *   mesh is uploaded once into a display list with client arrays, as the previous version did.
 * - framebuffer objects go through GTNHLib's {@code GLCaps.FBO}.
 */
public enum AtmosphereRenderer implements IGenericRenderer<AtmosphereSettings, EnumAtmospherePass, AtmosphereModel, StellarRenderInformation> {

	INSTANCE;

	private static final int STRIDE_IN_FLOAT = 8;

	private FramebufferCustom stellar = null;
	private int prevWidth = 0, prevHeight = 0;
	private int savedFramebuffer = 0;
	private final IntBuffer savedViewport = BufferUtils.createIntBuffer(16);

	private FloatBuffer renderBuffer;
	private ByteBuffer indicesBuffer;
	private int sphereList = -1;

	private boolean previousFlag;
	private boolean cacheChangedFlag = false;

	private AtmShaderManager shaderManager;

	AtmosphereRenderer() {
		this.shaderManager = new AtmShaderManager();
	}

	@Override
	public void initialize(AtmosphereSettings settings) {
		shaderManager.reloadShaders();

		if(!settings.checkChange())
			return;

		int renderBufferNewSize = (settings.fragLong + 1) * (settings.fragLat + 1) * STRIDE_IN_FLOAT;
		if(this.renderBuffer == null || renderBuffer.capacity() < renderBufferNewSize)
			this.renderBuffer = ByteBuffer.allocateDirect(renderBufferNewSize << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();

		int indicesBufferNewSize = (settings.fragLong * settings.fragLat * 4) << 2; // 4 Indices for Quad
		if(this.indicesBuffer == null || indicesBuffer.capacity() < indicesBufferNewSize)
			this.indicesBuffer = ByteBuffer.allocateDirect(indicesBufferNewSize).order(ByteOrder.nativeOrder());

		this.setupIndicesBuffer(settings.fragLong, settings.fragLat);

		this.cacheChangedFlag = true;
	}

	private void setupFramebuffer(int width, int height) {
		if(this.stellar != null)
			stellar.deleteFramebuffer();

		this.stellar = FramebufferCustom.builder()
				.texFormat(OpenGlUtil.RGB32F, GL11.GL_RGB, OpenGlUtil.TEXTURE_FLOAT)
				.depthStencil(true, false)
				.build(width, height);
	}

	@Override
	public void preRender(AtmosphereSettings settings, StellarRenderInformation info) {
		if(this.cacheChangedFlag || this.previousFlag != info.isFrameBufferEnabled) {
			this.reallocList(settings, info.isFrameBufferEnabled, info.deepDepth);
			this.previousFlag = info.isFrameBufferEnabled;
			this.cacheChangedFlag = false;
		}

		shaderManager.updateWorldInfo(info);

		if(info.isFrameBufferEnabled && GLCaps.fboSupported()) {
			if(info.minecraft.displayWidth != this.prevWidth || info.minecraft.displayHeight != this.prevHeight) {
				this.setupFramebuffer(info.minecraft.displayWidth, info.minecraft.displayHeight);
				this.prevWidth = info.minecraft.displayWidth;
				this.prevHeight = info.minecraft.displayHeight;
			}
		}
	}

	/** Gets the refraction amount on the boundary of the view. */
	public double getBoundaryRefraction(StellarRenderInformation info) {
		Entity viewer = info.minecraft.renderViewEntity;
		SpCoord coord = new SpCoord(0.0, 0.0);
		double boundHeight = Math.toDegrees(Math.atan(info.relativeHeight / 2));

		double height = coord.y = -viewer.rotationPitch + boundHeight;
		ExtinctionRefraction.refraction(coord, false);
		double ref1 = height - coord.y;

		height = coord.y = -viewer.rotationPitch - boundHeight;
		ExtinctionRefraction.refraction(coord, false);
		double ref2 = height - coord.y;

		return (ref1 + ref2) * 0.5;
	}

	@Override
	public void renderPass(AtmosphereModel model, EnumAtmospherePass pass, StellarRenderInformation info) {
		switch(pass) {
		case Prepare:
			if(info.isFrameBufferEnabled && this.stellar != null) {
				this.savedFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
				this.savedViewport.clear();
				GL11.glGetInteger(GL11.GL_VIEWPORT, this.savedViewport);

				stellar.bindFramebuffer(false);
				GL11.glDepthMask(true);
				stellar.framebufferClear();
				GL11.glDepthMask(false);

				GL11.glPushMatrix();

				// Pitch up to adjust the frustum in favour of refraction
				double ref = this.getBoundaryRefraction(info);
				info.boundaryRefraction = ref;
				Entity viewer = info.minecraft.renderViewEntity;
				GL11.glRotatef(270.0f - viewer.rotationYaw, 0.0f, 0.0f, 1.0f);
				GL11.glRotatef((float) ref, 0.0f, 1.0f, 0.0f);
				GL11.glRotatef(-(270.0f - viewer.rotationYaw), 0.0f, 0.0f, 1.0f);
			}
			break;

		case SetupDominateScatter:
			// Extinction first: glBlendFunc(ZERO, SRC_COLOR) leaves dst * src, so this darkens
			// and reddens exactly the light paths that reached the frame - including the stars
			// already drawn into it.
			GL11.glBlendFunc(GL11.GL_ZERO, GL11.GL_SRC_COLOR);
			shaderManager.bindExtinctionShader(model);
			GL11.glCallList(this.sphereList);
			GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

			info.setActiveShader(shaderManager.bindShader(model, EnumStellarPass.DominateScatter));

			// The tessellator asks for the dominate geometry once per light source; see
			// IDominateRenderer.
			info.setDominateRenderer((lightDir, red, green, blue) -> {
				shaderManager.setLightSource(lightDir, red, green, blue);
				GL11.glCallList(this.sphereList);
			});
			break;

		case Finalize:
			if(info.isFrameBufferEnabled && this.stellar != null) {
				GL11.glPopMatrix();

				OpenGlUtil.bindFramebuffer(OpenGlUtil.FRAMEBUFFER_GL, this.savedFramebuffer);
				GL11.glViewport(this.savedViewport.get(0), this.savedViewport.get(1),
						this.savedViewport.get(2), this.savedViewport.get(3));

				// Apply refraction while compositing the HDR sky to the screen. The composite
				// replaces the target rather than adding to it, so blending and depth testing
				// left over from the scatter passes have to be off.
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glDisable(GL11.GL_DEPTH_TEST);
				GL11.glDepthMask(false);

				shaderManager.bindRefractionShader(model, info);
				GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
				stellar.bindFramebufferTexture();
				this.renderScreenPart();
				stellar.unbindFramebufferTexture();

				ShaderHelper.getInstance().releaseCurrentShader();
			}
			info.setActiveShader(null);
			break;

		case SetupOpaque:
			info.setActiveShader(shaderManager.bindShader(model, EnumStellarPass.Opaque));
			break;
		case SetupOpaqueScatter:
			info.setActiveShader(shaderManager.bindShader(model, EnumStellarPass.OpaqueScatter));
			break;
		case SetupPointScatter:
			info.setActiveShader(shaderManager.bindShader(model, EnumStellarPass.PointScatter));
			break;
		case SetupSurfaceScatter:
			info.setActiveShader(shaderManager.bindShader(model, EnumStellarPass.SurfaceScatter));
			break;
		default:
			break;
		}
	}

	@Override
	public void postRender(AtmosphereSettings settings, StellarRenderInformation info) { }

	/** Draws a screen-covering quad; the refraction shader reprojects its texture coordinates. */
	private void renderScreenPart() {
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();

		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(-1.0, 1.0, 0.0, 0.0, 1.0);
		tessellator.addVertexWithUV(-1.0, -1.0, 0.0, 0.0, 0.0);
		tessellator.addVertexWithUV(1.0, -1.0, 0.0, 1.0, 0.0);
		tessellator.addVertexWithUV(1.0, 1.0, 0.0, 1.0, 1.0);
		tessellator.draw();

		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();
	}

	public void reallocList(AtmosphereSettings settings, boolean isFramebufferEnabled, double deepDepth) {
		Vector3[][] displayvec = Allocator.createAndInitialize(settings.fragLong + 1, settings.fragLat + 1);

		if(this.sphereList != -1) {
			GLAllocation.deleteDisplayLists(this.sphereList);
			this.sphereList = -1;
		}

		// View-space sphere: unit direction vectors scaled to the deep depth. The atmosphere
		// shaders normalise gl_Vertex, so these are directions, not a parameterisation.
		for(int longc=0; longc<=settings.fragLong; longc++)
			for(int latc=0; latc<=settings.fragLat; latc++)
				displayvec[longc][latc].set(new SpCoord(longc*360.0/settings.fragLong,
						180.0 * latc / settings.fragLat - 90.0).getVec());

		this.sphereList = GLAllocation.generateDisplayLists(1);
		GL11.glNewList(this.sphereList, GL11.GL_COMPILE);
		this.drawDisplay(displayvec, settings.fragLong, settings.fragLat, deepDepth, false, false);
		GL11.glEndList();
	}

	Vector3 temporal = new Vector3();

	private void setupIndicesBuffer(int fragLong, int fragLat) {
		indicesBuffer.clear();
		for(int longc=0; longc < fragLong; longc++) {
			for(int latc=0; latc < fragLat; latc++) {
				indicesBuffer.putInt(((fragLat + 1) * longc + latc));
				indicesBuffer.putInt(((fragLat + 1) * longc + latc + 1));
				indicesBuffer.putInt(((fragLat + 1) * (longc + 1) + latc + 1));
				indicesBuffer.putInt(((fragLat + 1) * (longc + 1) + latc));
			}
		}
	}

	private void drawDisplay(Vector3[][] displayvec, int fragLong, int fragLat, double length, boolean hasTexture, boolean hasNormal) {
		renderBuffer.clear();

		short longc = 0, latc;
		for(Vector3[] vertRow : displayvec) {
			latc = 0;
			for(Vector3 pos : vertRow) {
				temporal.set(pos).scale(length);
				renderBuffer.put((float)temporal.getX());
				renderBuffer.put((float)temporal.getY());
				renderBuffer.put((float)temporal.getZ());

				renderBuffer.put(((float)longc) / fragLong);
				renderBuffer.put(((float)latc) / fragLat);

				renderBuffer.put((float)pos.getX());
				renderBuffer.put((float)pos.getY());
				renderBuffer.put((float)pos.getZ());

				latc++;
			}
			longc++;
		}

		renderBuffer.position(0);
		GL11.glVertexPointer(3, STRIDE_IN_FLOAT << 2, this.renderBuffer);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

		if(hasTexture) {
			renderBuffer.position(3);
			GL11.glTexCoordPointer(2, STRIDE_IN_FLOAT << 2, this.renderBuffer);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}

		if(hasNormal) {
			renderBuffer.position(5);
			GL11.glNormalPointer(STRIDE_IN_FLOAT << 2, this.renderBuffer);
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
		}

		indicesBuffer.position(0);
		GL11.glDrawElements(GL11.GL_QUADS, fragLong * fragLat * 4, GL11.GL_UNSIGNED_INT, this.indicesBuffer);

		if(hasNormal)
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);

		if(hasTexture)
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
	}
}
