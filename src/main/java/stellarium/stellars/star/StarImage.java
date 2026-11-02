package stellarium.stellars.star;

import stellarapi.api.CelestialPeriod;
import stellarapi.api.ICelestialCoordinate;
import stellarapi.api.ISkyEffect;
import stellarapi.api.celestials.EnumCelestialObjectType;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Vector3;
import stellarapi.api.optics.Wavelength;
import stellarium.stellars.layer.IPerWorldImage;

public class StarImage implements IPerWorldImage<BgStar> {
	
	private CelestialPeriod horizontalPeriod;
	private BgStar main;
	
	private String constellation;
	private short flamsteedId = 0;
	private String bayerId;
	private byte bayerSubId = 0;
	private String binary;
	
	private ICelestialCoordinate coordinate;
	private ISkyEffect sky;
	
	@Override
	public void initialize(BgStar object, ICelestialCoordinate coordinate, ISkyEffect effect, CelestialPeriod year) {
		this.main = object;
		this.coordinate = coordinate;
		this.sky = effect;
		
		SpCoord coord = new SpCoord();
		coord.setWithVec(object.pos);
		this.horizontalPeriod = new CelestialPeriod(String.format("Day; Star %s", main.name),
				coordinate.getPeriod().getPeriodLength(),
				coordinate.calculateInitialOffset(object.pos, coordinate.getPeriod().getPeriodLength()));
		
		this.constellation = object.name.substring(7);
		if(!object.name.substring(0,3).equals("   "))
			this.flamsteedId = Short.valueOf(object.name.substring(0, 3).trim());
		this.bayerId = object.name.substring(3, 6);
		if(!object.name.substring(6,7).equals(" "))
			this.bayerSubId = Byte.valueOf(object.name.substring(6,7));
		
		//this.binary = object.name.substring(49, 51);
		//  50- 51  A2     ---     ADScomp  ADS number components
		//  This covers every binaries, (Alp1/2 Cen: A/B)
	}
	
	/** Positions are worked out on demand, see {@link #getCurrentHorizontalPos()}. */
	@Override
	public void updateCache(BgStar object, ICelestialCoordinate coordinate, ISkyEffect sky) { }

	@Override
	public boolean needsTickUpdate() {
		return false;
	}
	
	@Override
	public CelestialPeriod getAbsolutePeriod() {
		return null;
	}

	@Override
	public CelestialPeriod getHorizontalPeriod() {
		return this.horizontalPeriod;
	}

	@Override
	public CelestialPeriod getPhasePeriod() {
		return null;
	}

	@Override
	public double getCurrentPhase() {
		return 1.0;
	}

	@Override
	public double getCurrentBrightness(Wavelength wavelength) {
		return 1.0;
	}

	@Override
	public Vector3 getCurrentAbsolutePos() {
		return main.pos;
	}

	@Override
	public SpCoord getCurrentHorizontalPos() {
		// The coordinate is updated every tick, so this is always current. Nothing reads star
		// positions on most ticks; the star field itself is rendered on the GPU.
		Vector3 ref = new Vector3(main.pos);
		coordinate.getProjectionToGround().transform(ref);
		SpCoord appPos = new SpCoord();
		appPos.setWithVec(ref);
		sky.applyAtmRefraction(appPos);
		return appPos;
	}

	@Override
	public double getStandardMagnitude() {
		return main.mag;
	}

	@Override
	public EnumCelestialObjectType getObjectType() {
		return EnumCelestialObjectType.Star;
	}

	@Override
	public String getName() {
		return (this.constellation + this.flamsteedId + this.bayerId).trim();
	}
}
