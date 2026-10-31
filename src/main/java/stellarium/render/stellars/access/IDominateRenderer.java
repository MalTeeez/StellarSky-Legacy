package stellarium.render.stellars.access;

import stellarapi.api.lib.math.Vector3;

/**
 * Draws the dominate-scatter geometry (the atmosphere sphere) for one light source.
 *
 * Replaces the old "pass a display list id around" mechanism, so the atmosphere renderer
 * decides what to draw instead of the tessellator assuming a display list.
 */
public interface IDominateRenderer {
	public void renderDominate(Vector3 lightDir, float red, float green, float blue);
}
