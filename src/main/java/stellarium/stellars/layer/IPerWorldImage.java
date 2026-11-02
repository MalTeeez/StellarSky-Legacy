package stellarium.stellars.layer;

import stellarapi.api.CelestialPeriod;
import stellarapi.api.ICelestialCoordinate;
import stellarapi.api.ISkyEffect;
import stellarapi.api.celestials.ICelestialObject;

public interface IPerWorldImage<Obj extends StellarObject> extends ICelestialObject {
	
	public void initialize(Obj object, ICelestialCoordinate coordinate, ISkyEffect sky, CelestialPeriod yearPeriod);
	public void updateCache(Obj object, ICelestialCoordinate coordinate, ISkyEffect sky);

	/**
	 * False for images that work out their state when asked instead, so the collection can skip
	 * them on its per-tick update (thousands of stars that nothing reads most ticks).
	 */
	public default boolean needsTickUpdate() {
		return true;
	}

}
