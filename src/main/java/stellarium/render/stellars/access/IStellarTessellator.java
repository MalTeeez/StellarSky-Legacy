package stellarium.render.stellars.access;

import net.minecraft.util.ResourceLocation;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Vector3;

public interface IStellarTessellator {
	
	public void bindTexture(ResourceLocation location);
	
	public void begin(boolean hasNormal);

	/**
	 * Starts a batch of screen-facing point quads. Each {@link #point} emits its own four
	 * corners, so the falloff can use texcoords the way 1.12's {@code renderPoint} does,
	 * instead of relying on gl_PointCoord.
	 */
	public void beginPoint();

	/** Emits one screen-facing quad of about a pixel, centred on the unit direction given. */
	public void point(Vector3 direction, float red, float green, float blue);
	
	public void pos(SpCoord pos, float depth);

	/**
	 * Appends color <b>without</b> scope & filter.
	 * */
	public void color(float red, float green, float blue);
	
	/** For textured objects */
	public void texture(float u, float v);
	
	/** For textured objects */
	public void normal(Vector3 normal);
	
	public void writeVertex();
	
	/**
	 * For opaque scatterings.
	 * Limited to once per call.
	 *  */
	public void radius(float angularRadius);
	
	public void end();


	/**
	 * Internal method
	 * */
	public void initializePass(EnumStellarPass pass);

}
