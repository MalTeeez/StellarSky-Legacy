package stellarium.stellars;

import net.minecraftforge.common.config.Configuration;
import stellarapi.api.lib.config.SimpleConfigHandler;
import stellarapi.api.lib.config.property.ConfigPropertyDouble;
import stellarapi.api.lib.math.Spmath;
import stellarapi.api.optics.EyeDetector;
import stellarium.StellarSky;
import stellarium.util.math.CachedGaussianRandom;

public class OpticsHelper extends SimpleConfigHandler {
	public static final double ext_coeff_B=0.3;
	public static final double ext_coeff_V=0.2;
	public static final double ext_coeff_R=0.1;
	
	// MagOffset needs to be changed to whatever the maximum Magnitude of Venus is 
	//private static final float magOffset = 5.50f;
	private static final float magnitudeBase = 2.512f;
	//private static final float magCompressionBase = 6.50f;

	private static final double sunMagnitude = -26.74;
	// -0.5 in 1.12 (was -1.0 here); the HDR chain depends on this calibration.
	private static final double upperMagLimit = -0.5;
	
	private static CachedGaussianRandom randomTurbulance = new CachedGaussianRandom(100, 3L);

	// Naked eye resolution in rad
	public static final float DEFAULT_RESOLUTION = (float) Math.toRadians(EyeDetector.DEFAULT_RESOLUTION);

	// Relative brightness of an object of size 1 (rad)^2 compared to that star (same flux)
	private static final float SURF_MULTIPLIER = (float) (2 * Math.PI * Spmath.sqr(DEFAULT_RESOLUTION));

	/** Gets the multiplier for an object spread over {@code angularArea} (rad)^2. */
	public static float getMultFromArea(double angularArea) {
		return SURF_MULTIPLIER / (float) angularArea;
	}
	
	public static final OpticsHelper instance = new OpticsHelper();
	
	private ConfigPropertyDouble propBrightnessContrast;
	private ConfigPropertyDouble propTurb;
	private ConfigPropertyDouble propExposure;
	
	private double brightnessContrast = 2.0;
	//private float magCompression;
	//private float magContrast;
	private float brightnessPower;
	private double turbulance;
	
	public OpticsHelper() {
		this.propBrightnessContrast = new ConfigPropertyDouble("Brightness_Contrast", "", 2.0);
		this.propExposure = new ConfigPropertyDouble("Exposure", "", 1.0);
		this.propTurb = new ConfigPropertyDouble("Twinkling(Turbulance)", "", 1.0);
		
		this.addConfigProperty(this.propBrightnessContrast);
		this.addConfigProperty(this.propExposure);
		this.addConfigProperty(this.propTurb);
	}
	
	@Override
	public void setupConfig(Configuration config, String category) {
		config.setCategoryLanguageKey(category, "config.category.optics");
		config.setCategoryComment(category, "Configuration for Optical settings.");
		config.setCategoryRequiresMcRestart(category, false);
		
		super.setupConfig(config, category);
		
		propBrightnessContrast.setComment("Brightness Contrast determines the contrast "
				+ "between bright stars and faint stars. "
				+ "The bigger the value, the less difference between bright stars and faint stars. "
				+ "Real world (minimum) = 1.0. Default = 2.0 for visual effect.");
		propBrightnessContrast.setRequiresMcRestart(false);
		propBrightnessContrast.setLanguageKey("config.property.client.brcontrast");
		propBrightnessContrast.setMaxValue(4.0);
		propBrightnessContrast.setMinValue(0.5);
		
        propExposure.setComment("Exposure of the sky. Raise it if the sky is too bright, lower it if too dark.");
        propExposure.setRequiresMcRestart(false);
        propExposure.setLanguageKey("config.property.client.exposure");
        propExposure.setMinValue(0.1);
        propExposure.setMaxValue(10.0);

        propTurb.setComment("Degree of the twinkling effect of star.\n"
        		+ "It determines the turbulance of atmosphere, which actually cause the twinkling effect. "
				+ "The greater the value, the more the stars will twinkle. Default is 1.0. To disable set to 0.0");
        propTurb.setRequiresMcRestart(false);
        propTurb.setLanguageKey("config.property.client.turbulance");
        propTurb.setMinValue(0.0);
        propTurb.setMaxValue(2.0);
	}

	@Override
	public void loadFromConfig(Configuration config, String category) {
		super.loadFromConfig(config, category);
		
		this.brightnessContrast = propBrightnessContrast.getDouble();
		this.turbulance = propTurb.getDouble() * 4.0;
		this.brightnessPower = 1.0f / ((float)this.brightnessContrast);
	}
	
	@Override
	public void saveToConfig(Configuration config, String category) { }
	
	/*public static float getAlphaFromMagnitudeSparkling(float Mag, float bglight){
		double turb = randomTurbulance.nextGaussian() * (instance.turbulance / (Mag + 4.46f));
		return getAlpha(((Mag + upperMagLimit) * instance.magCompression) + turb, bglight);
	}

	public static float getAlphaFromMagnitude(double Mag, float bglight) {
		return getAlpha(Mag, bglight);
	}
	
	public static float getAlphaForGalaxy(double Mag, float bglight) {
		return getAlpha(((Mag + upperMagLimit) * instance.magCompression), bglight);
	}*/

	/*public static final double constantBgDiv = Math.log(2.1333334f + 1.0f);

	private static float getAlpha(double magCompressed, float bglight) {
		return (float) ((Math.pow(instance.magContrast,
				- magCompressed - magOffset *
				((Math.pow(Math.log(bglight + 1.0f)/constantBgDiv, magOffset)))))
				- (bglight / 2.1333334f));
	}*/

	/** User exposure multiplier from the optics config. */
	public static double getExposure() {
		return instance == null ? 1.0 : instance.propExposure.getDouble();
	}

	public static float turbulance() {
		return (float) (instance.turbulance * instance.randomTurbulance.nextGaussian() * 0.1);
	}

	/** Standard deviation, in magnitudes, of {@link #turbulance()}. */
	public static double getTurbulenceStd() {
		return instance.turbulance * 0.1;
	}

	public static float getBrightnessFromMagnitude(double magnitude) {
		return (float) Math.pow(magnitudeBase, upperMagLimit - magnitude);
	}
	
	public static float getDominationFromMagnitude(double magnitude) {
		return (float) Math.pow(magnitudeBase, sunMagnitude - magnitude);
	}

	public static float getCompressed(float brightness) {
		return (float) Math.pow(brightness, instance.brightnessPower);
	}
}