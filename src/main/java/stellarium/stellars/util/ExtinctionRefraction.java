package stellarium.stellars.util;

import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Spmath;

//Will be corrected
public class ExtinctionRefraction {
	
	static final double subhorizontal_airmass=30.0;
	
	//Calculate Airmass
	static double airmass(double cosZ, boolean IsApparent)
	{
		cosZ=Math.abs(cosZ);
		
		double am;
		if (IsApparent)
		{
			// Rozenberg (1966)
			am= (1.0/(cosZ+0.025*Math.exp(-11.0*cosZ)));
		}
		else
		{
			//Young (1994)
			double up=(1.002432*cosZ+0.148386)*cosZ+0.0096467;
			double down=((cosZ+0.149864)*cosZ+0.0102963)*cosZ+0.000303978;
			am=up/down;
		}
		return am;
	}
	
	//Get Extinction magnitude(in V band) of EVectortor(its size must be 1)
	public static double airmass(SpCoord coord, boolean IsApparent){
		return airmass(Spmath.sind(coord.y), IsApparent);
	}
	
	/**
	 * Both formulas are meant for objects above the horizon and have poles just below it (-5.11 and -4.4 degrees);
	 * the elevation they see is clamped so points further down (e.g. the bottom edge of a wide view, see
	 * AtmosphereRenderer#getBoundaryRefraction) get a finite refraction instead of an arbitrary huge one.
	 */
	static final double MIN_REFRACTION_HEIGHT = -1.0;

	public static void refraction(SpCoord sp, boolean isApplying) {
		double R;
		double h = Math.max(sp.y, MIN_REFRACTION_HEIGHT);

 		if(isApplying)
		{
			//Saemundsson (1986)
 			R=1.02/Spmath.tand(h+10.3/(h+5.11));
 			sp.y+=R/60.0;
		}
		else
		{
			//Garfinkel (1967)
			R=1.0/Spmath.tand(h+7.31/(h+4.4));
			sp.y-=R/60.0;
		}
	}
}
