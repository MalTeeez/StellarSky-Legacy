package stellarium.render.stellars.access;

import stellarapi.api.lib.math.SpCoord;

public interface ICheckedAtmModel {
	public boolean isRendered(SpCoord pos);

	/** Least apparent altitude (in the units {@link #isRendered} compares) still rendered. */
	public double getLeastAltitudeRendered();
}
