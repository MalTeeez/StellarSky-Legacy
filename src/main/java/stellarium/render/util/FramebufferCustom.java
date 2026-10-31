package stellarium.render.util;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.EXTPackedDepthStencil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import com.gtnewhorizon.gtnhlib.client.opengl.FBOFunctions;
import com.gtnewhorizon.gtnhlib.client.opengl.GLCaps;

import net.minecraft.client.renderer.Tessellator;
import stellarium.StellarSky;

/**
 * Framebuffer with a configurable colour format, depth/stencil and render region.
 *
 * Ported from 1.12's {@code stellarium.render.util.FramebufferCustom}. It does not extend
 * {@code net.minecraft.client.shader.Framebuffer} and it does not use {@code GlStateManager},
 * because 1.7.10 has neither a usable general framebuffer API nor a GlStateManager (that class
 * arrived in 1.8).
 *
 * Framebuffer object calls go through GTNHLib's {@link GLCaps#FBO}, which picks the EXT or core
 * entry points at runtime; that is the supported surface, rather than calling
 * {@code GL30}/{@code EXT*}/{@code OpenGlHelper.func_*} by hand. Everything else is plain
 * {@code GL11}, which is what Angelica's bytecode redirector rewrites into its own
 * {@code GLStateManager} (see {@code com.gtnewhorizons.angelica.api.GLRedirects}).
 */
public class FramebufferCustom {
	private int width, height;
	private int textureWidth, textureHeight;
	private int renderX, renderY, renderWidth, renderHeight;

	private int internalFormat, format, type;

	private int minFilter, magFilter;

	private boolean useDepth, useStencil;

	private int framebufferPointer = -1;
	private int frameTexturePointer = -1;
	private int depthBufferPointer = -1;

	private final float[] clearColor = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
	private float clearDepth = 1.0f;

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private boolean useDepth = true, useStencil = false;

		private int textureWidth = -1, textureHeight = -1;
		private int internalFormat = GL11.GL_RGBA8, format = GL11.GL_RGBA, type = GL11.GL_UNSIGNED_BYTE;
		private int minFilter = GL11.GL_NEAREST, magFilter = GL11.GL_NEAREST;
		private int renderX = 0, renderY = 0, renderWidth = -1, renderHeight = -1;

		public Builder depthStencil(boolean useDepth, boolean useStencil) {
			this.useDepth = useDepth;
			this.useStencil = useStencil;
			return this;
		}

		public Builder texFormat(int internalFormat, int format, int type) {
			this.internalFormat = internalFormat;
			this.format = format;
			this.type = type;
			return this;
		}

		public Builder texture(int texWidth, int texHeight) {
			this.textureWidth = texWidth;
			this.textureHeight = texHeight;
			return this;
		}

		public Builder texMinMagFilter(int minFilter, int magFilter) {
			this.minFilter = minFilter;
			this.magFilter = magFilter;
			return this;
		}

		public Builder renderRegion(int x, int y, int width, int height) {
			this.renderX = x;
			this.renderY = y;
			this.renderWidth = width;
			this.renderHeight = height;
			return this;
		}

		public FramebufferCustom build(int width, int height) {
			FramebufferCustom built = new FramebufferCustom();
			built.width = width;
			built.height = height;

			built.useDepth = this.useDepth;
			built.useStencil = this.useStencil;

			built.textureWidth = this.textureWidth > 0 ? this.textureWidth : built.width;
			built.textureHeight = this.textureHeight > 0 ? this.textureHeight : built.height;

			built.minFilter = this.minFilter;
			built.magFilter = this.magFilter;

			built.renderX = this.renderX;
			built.renderY = this.renderY;
			built.renderWidth = this.renderWidth > 0 ? this.renderWidth : built.width;
			built.renderHeight = this.renderHeight > 0 ? this.renderHeight : built.height;

			built.internalFormat = this.internalFormat;
			built.format = this.format;
			built.type = this.type;

			built.createFramebuffer();
			return built;
		}
	}

	private FramebufferCustom() {
	}

	private void createFramebuffer() {
		this.framebufferPointer = GLCaps.FBO.glGenFramebuffers();
		this.frameTexturePointer = GL11.glGenTextures();

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.frameTexturePointer);
		this.setTexMinMagFilter(this.minFilter, this.magFilter);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, this.internalFormat, this.textureWidth,
				this.textureHeight, 0, this.format, this.type, (ByteBuffer) null);

		GLCaps.FBO.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferPointer);
		GLCaps.FBO.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
				GL11.GL_TEXTURE_2D, this.frameTexturePointer, 0);

		if (this.useDepth) {
			this.depthBufferPointer = GLCaps.FBO.glGenRenderbuffers();
			GLCaps.FBO.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.depthBufferPointer);
			if (!this.useStencil) {
				GLCaps.FBO.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24,
						this.textureWidth, this.textureHeight);
				GLCaps.FBO.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
						GL30.GL_RENDERBUFFER, this.depthBufferPointer);
			} else {
				GLCaps.FBO.glRenderbufferStorage(GL30.GL_RENDERBUFFER,
						EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT, this.textureWidth, this.textureHeight);
				GLCaps.FBO.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
						GL30.GL_RENDERBUFFER, this.depthBufferPointer);
				GLCaps.FBO.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
						GL30.GL_RENDERBUFFER, this.depthBufferPointer);
			}
		}

		this.framebufferClear();
		this.checkFramebufferComplete();
		GLCaps.FBO.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	public void deleteFramebuffer() {
		if (this.depthBufferPointer > -1) {
			GLCaps.FBO.glDeleteRenderbuffers(this.depthBufferPointer);
			this.depthBufferPointer = -1;
		}

		if (this.frameTexturePointer > -1) {
			GL11.glDeleteTextures(this.frameTexturePointer);
			this.frameTexturePointer = -1;
		}

		if (this.framebufferPointer > -1) {
			GLCaps.FBO.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			GLCaps.FBO.glDeleteFramebuffers(this.framebufferPointer);
			this.framebufferPointer = -1;
		}
	}

	public void checkFramebufferComplete() {
		int status = GLCaps.FBO.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
		if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
			throw new RuntimeException("Framebuffer with format " + this.internalFormat
					+ " is incomplete, glCheckFramebufferStatus returned 0x" + Integer.toHexString(status));
		StellarSky.logger.info("Framebuffer with format " + this.internalFormat + " is complete");
	}

	public void bindFramebufferTexture() {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.frameTexturePointer);
	}

	public void unbindFramebufferTexture() {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	public void bindFramebuffer(boolean updateViewport) {
		GLCaps.FBO.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferPointer);

		if (updateViewport)
			GL11.glViewport(this.renderX, this.renderY, this.renderWidth, this.renderHeight);
	}

	public void framebufferClear() {
		GL11.glClearColor(this.clearColor[0], this.clearColor[1], this.clearColor[2], this.clearColor[3]);
		int flag = GL11.GL_COLOR_BUFFER_BIT;

		if (this.useDepth) {
			GL11.glClearDepth(this.clearDepth);
			flag |= GL11.GL_DEPTH_BUFFER_BIT;
		}

		GL11.glClear(flag);
	}

	public void renderFullQuad() {
		// This is a screen-covering quad drawn at z=0. If depth testing or depth writes are
		// left on it stamps depth 0 across the frame, and everything drawn afterwards - the
		// terrain - fails the depth test. 1.12's equivalent ran under GlStateManager state it
		// never disturbed; here it has to be explicit.
		boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);

		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();

		float u0 = (float) this.renderX / this.width;
		float v0 = (float) this.renderY / this.height;
		float u1 = (float) (this.renderX + this.renderWidth) / this.width;
		float v1 = (float) (this.renderY + this.renderHeight) / this.height;

		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(-1.0, 1.0, 0.0, u0, v1);
		tessellator.addVertexWithUV(-1.0, -1.0, 0.0, u0, v0);
		tessellator.addVertexWithUV(1.0, -1.0, 0.0, u1, v0);
		tessellator.addVertexWithUV(1.0, 1.0, 0.0, u1, v1);
		tessellator.draw();

		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();

		GL11.glDepthMask(depthMask);
		if (depthTest)
			GL11.glEnable(GL11.GL_DEPTH_TEST);
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public void setClearColor(float red, float green, float blue, float alpha) {
		this.clearColor[0] = red;
		this.clearColor[1] = green;
		this.clearColor[2] = blue;
		this.clearColor[3] = alpha;
	}

	public void setClearDepth(float depth) {
		this.clearDepth = depth;
	}

	private void setTexMinMagFilter(int minFilter, int magFilter) {
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
	}
}
