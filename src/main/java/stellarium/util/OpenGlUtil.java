package stellarium.util;

import org.lwjgl.opengl.ARBHalfFloatPixel;
import org.lwjgl.opengl.ARBPointSprite;
import org.lwjgl.opengl.ARBPixelBufferObject;
import org.lwjgl.opengl.ARBTextureFloat;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.NVHalfFloat;

import com.gtnewhorizon.gtnhlib.client.opengl.GLCaps;

/**
 * Ported subset of 1.12's {@code stellarium.util.OpenGlUtil}: the format constants and
 * capability probes the HDR post-processing chain needs.
 *
 * Framebuffer object entry points are not here - they belong to GTNHLib's
 * {@link GLCaps#FBO}, which picks EXT or core at runtime.
 */
public class OpenGlUtil {

	private static final int POINT_SPRITE;
	public static final int PIXEL_PACK_BUFFER;

	public static final int RGB16F, RGBA16F, RGB32F, RGBA32F;
	public static final int TEXTURE_FLOAT;

	public static final boolean HALF_FLOAT_SUPPORTED;
	public static final int HALF_FLOAT;

	public static final int FRAMEBUFFER_GL;
	public static final int FRAMEBUFFER_BINDING;

	static {
		ContextCapabilities caps = GLContext.getCapabilities();
		POINT_SPRITE = caps.OpenGL20 ? GL20.GL_POINT_SPRITE : ARBPointSprite.GL_POINT_SPRITE_ARB;
		PIXEL_PACK_BUFFER = caps.OpenGL21 ? GL21.GL_PIXEL_PACK_BUFFER
				: ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB;

		RGB16F = caps.GL_ARB_texture_float ? (caps.OpenGL30 ? GL30.GL_RGB16F : ARBTextureFloat.GL_RGB16F_ARB)
				: GL11.GL_RGB16;
		RGBA16F = caps.GL_ARB_texture_float ? (caps.OpenGL30 ? GL30.GL_RGBA16F : ARBTextureFloat.GL_RGBA16F_ARB)
				: GL11.GL_RGBA16;
		RGB32F = caps.GL_ARB_texture_float ? (caps.OpenGL30 ? GL30.GL_RGB32F : ARBTextureFloat.GL_RGB32F_ARB)
				: GL11.GL_RGB16;
		RGBA32F = caps.GL_ARB_texture_float ? (caps.OpenGL30 ? GL30.GL_RGBA32F : ARBTextureFloat.GL_RGBA32F_ARB)
				: GL11.GL_RGBA16;
		TEXTURE_FLOAT = caps.GL_ARB_texture_float ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_SHORT;

		if (caps.OpenGL30) {
			HALF_FLOAT_SUPPORTED = true;
			HALF_FLOAT = GL30.GL_HALF_FLOAT;
		} else if (caps.GL_ARB_half_float_pixel) {
			HALF_FLOAT_SUPPORTED = true;
			HALF_FLOAT = ARBHalfFloatPixel.GL_HALF_FLOAT_ARB;
		} else if (caps.GL_NV_half_float) {
			HALF_FLOAT_SUPPORTED = true;
			HALF_FLOAT = NVHalfFloat.GL_HALF_FLOAT_NV;
		} else {
			HALF_FLOAT_SUPPORTED = false;
			HALF_FLOAT = 0;
		}

		FRAMEBUFFER_GL = GL30.GL_FRAMEBUFFER;
		FRAMEBUFFER_BINDING = GL30.GL_DRAW_FRAMEBUFFER_BINDING;
	}

	/** Enables point sprites, unless the context is a core profile where they are always on. */
	public static void enablePointSprite() {
		if (!isCoreProfile())
			GL11.glEnable(POINT_SPRITE);
	}

	public static void disablePointSprite() {
		if (!isCoreProfile())
			GL11.glDisable(POINT_SPRITE);
	}

	private static boolean isCoreProfile() {
		ContextCapabilities caps = GLContext.getCapabilities();
		return caps.OpenGL32 && !caps.GL_ARB_compatibility;
	}

	public static void bindFramebuffer(int target, int framebuffer) {
		GLCaps.FBO.glBindFramebuffer(target, framebuffer);
	}

	public static int generationMipmapTarget = GL11.GL_TEXTURE_2D;

	public static void generateMipmap(int target) {
		GL30.glGenerateMipmap(target);
	}
}
