package stellarium.render.stellars.access;

import net.minecraft.util.ResourceLocation;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Vector3;

public interface IStellarTessellator {
	
	public void bindTexture(ResourceLocation location);
	
	public void begin();

	public void pos(SpCoord pos, float depth);

	/**
	 * Appends color <b>without</b> scope & filter.
	 * */
	public void color(float red, float green, float blue);
	
	/** For textured objects */
	public void texture(float u, float v);
	
	public void writeVertex();
	
	/**
	 * For opaque scatterings.
	 * Limited to once per call.
	 *  */
	public void radius(float angularRadius);
	
	public void end();

	/** Whether anything within {@code angularRadius} (radians) of the unit horizontal direction can be on screen. */
	public boolean inView(Vector3 direction, double angularRadius);


	/**
	 * Internal method
	 * */
	public void initializePass(EnumStellarPass pass);

}
