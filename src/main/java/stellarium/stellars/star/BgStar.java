package stellarium.stellars.star;

import stellarapi.api.lib.math.Vector3;
import stellarium.stellars.layer.StellarObject;

public class BgStar extends StellarObject {

	protected String name;
	protected int number;
	protected double mag;
	protected double B_V;
	protected Vector3 pos;
	
	public BgStar(String name, int number, double mag, double B_V, Vector3 pos) {
		this.name = name;
		this.number = number;
		this.mag = mag;
		this.B_V = B_V;
		this.pos = pos;
	}

	private String id;

	// Used by StellarObject.hashCode/equals on every per-tick image lookup; cache instead of re-stringifying.
	@Override
	public String getID() {
		if(this.id == null)
			this.id = String.valueOf(this.number);
		return this.id;
	}

	public String getName() {
		return this.name;
	}
	
	public int getNumber() {
		return this.number;
	}

}
