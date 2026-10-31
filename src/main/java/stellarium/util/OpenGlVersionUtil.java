package stellarium.util;

import org.lwjgl.opengl.ARBPointSprite;
import org.lwjgl.opengl.ARBTextureFloat;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

public class OpenGlVersionUtil {
	
	private static int spriteFlag;
	private static int rgb16f;
	
	/**
	 * True when running on a core profile (e.g. Angelica, which renders through a
	 * GL 3.2 core context). A 2.1 compatibility context is not a core profile and
	 * does not advertise ARB_compatibility either, hence the version check.
	 */
	private static final boolean coreProfile;
	
	static {
		ContextCapabilities contextcapabilities = GLContext.getCapabilities();
		spriteFlag = contextcapabilities.OpenGL20? GL20.GL_POINT_SPRITE : ARBPointSprite.GL_POINT_SPRITE_ARB;
		rgb16f = contextcapabilities.OpenGL30? GL30.GL_RGB16F :
			(contextcapabilities.GL_ARB_texture_float? ARBTextureFloat.GL_RGB16F_ARB : GL11.GL_RGB);
		coreProfile = contextcapabilities.OpenGL32 && !contextcapabilities.GL_ARB_compatibility;
	}

	public static void enablePointSprite() {
		// GL_POINT_SPRITE was removed in the core profile, where point sprites are
		// always enabled; enabling it there only raises GL_INVALID_ENUM.
		if(!coreProfile)
			GL11.glEnable(spriteFlag);
	}
	
	public static void disablePointSprite() {
		if(!coreProfile)
			GL11.glDisable(spriteFlag);
	}
	
	public static int rgb16f() {
		return rgb16f;
	}
}
