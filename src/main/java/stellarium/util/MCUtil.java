package stellarium.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import net.minecraft.client.renderer.EntityRenderer;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Ported from 1.12's {@code stellarium.util.MCUtil}: {@code EntityRenderer.getFOVModifier} is
 * private, so it is reached by reflection.
 */
public class MCUtil {
	private static final Method GET_FOV_MODIFIER = ReflectionHelper.findMethod(EntityRenderer.class,
			null, new String[] { "getFOVModifier", "func_78481_a" }, float.class, boolean.class);
	private static final MethodHandle GFM_HANDLE;

	static {
		MethodHandle handle = null;
		try {
			handle = MethodHandles.lookup().unreflect(GET_FOV_MODIFIER);
		} catch (IllegalAccessException exc) {
			throw new RuntimeException("Could not access EntityRenderer#getFOVModifier", exc);
		}

		GFM_HANDLE = handle;
	}

	public static float getFOVModifier(EntityRenderer renderer, float partialTicks, boolean useFOVSetting) {
		try {
			return (float) GFM_HANDLE.invokeExact(renderer, partialTicks, useFOVSetting);
		} catch (Throwable exc) {
			throw new RuntimeException(exc);
		}
	}
}
