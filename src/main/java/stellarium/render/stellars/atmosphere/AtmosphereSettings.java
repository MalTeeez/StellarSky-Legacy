package stellarium.render.stellars.atmosphere;

import net.minecraftforge.common.config.Configuration;
import stellarapi.api.lib.config.SimpleConfigHandler;
import stellarapi.api.lib.config.property.ConfigPropertyInteger;

public class AtmosphereSettings extends SimpleConfigHandler {
	
	public static final String KEY = "atmosphere";
	
	private ConfigPropertyInteger propFragSize;
	private ConfigPropertyInteger propScreenFragSize;

	/**
	 * Number of atmosphere fragments
	 * */
	public int fragLong = 256, fragLat = 128;
	
	
	private boolean isChanged = true;
	
	public AtmosphereSettings() {
		this.propFragSize = new ConfigPropertyInteger("Atmosphere_Fragment_Number", "", 128);
		this.propScreenFragSize = new ConfigPropertyInteger("Screen_Fragment_Number", "", 64);

		this.addConfigProperty(this.propFragSize);
		this.addConfigProperty(this.propScreenFragSize);
	}
	
	@Override
	public void setupConfig(Configuration config, String category) {
		config.setCategoryComment(category, "Configurations for the atmosphere accuracy.");
		config.setCategoryLanguageKey(category, "config.category.atmosphere");
		config.setCategoryRequiresMcRestart(category, false);
		
		super.setupConfig(config, category);
		
		propFragSize.setComment("Atmosphere is drawn with fragments\n" +
        		"Less fragments will increase FPS, but the atmosphere will become more defective.");
		propFragSize.setRequiresMcRestart(false);
		propFragSize.setLanguageKey("config.property.atmosphere.fragnumber");
		propFragSize.setMinValue(8);
       	propFragSize.setMaxValue(512);

       	propScreenFragSize.setComment("Number of screen fragments used to composite the sky.");
       	propScreenFragSize.setRequiresMcRestart(false);
       	propScreenFragSize.setLanguageKey("config.property.atmosphere.screenfragnumber");
       	propScreenFragSize.setMinValue(8);
       	propScreenFragSize.setMaxValue(512);

	}

	@Override
	public void loadFromConfig(Configuration config, String category) {
		super.loadFromConfig(config, category);
		
		int fragNumber = propFragSize.getInt();
		if(this.fragLat != fragNumber) {
			this.isChanged = true;
			this.fragLat = fragNumber;
			this.fragLong = 2 * fragNumber;
		}
		
	}
	
	@Override
	public void saveToConfig(Configuration config, String category) {
		super.saveToConfig(config, category);
	}

	public boolean checkChange() {
		boolean ret = this.isChanged;
		this.isChanged = false;
		return true;
	}

}
