package stellarapi.api.gui.overlay;

import net.minecraftforge.common.config.Configuration;
import stellarapi.api.gui.pos.EnumHorizontalPos;
import stellarapi.api.gui.pos.EnumVerticalPos;
import stellarapi.api.lib.config.SimpleConfigHandler;
import stellarapi.api.lib.config.property.ConfigPropertyBoolean;
import stellarapi.api.lib.config.property.ConfigPropertyString;

public class PerOverlaySettings extends SimpleConfigHandler {

	private EnumHorizontalPos horizontal;
	private EnumVerticalPos vertical;
	private boolean visibleOnMain;
	private boolean canOnMain;
	
	private ConfigPropertyString propHorizontal;
	private ConfigPropertyString propVertical;
	private ConfigPropertyBoolean propVisibleOnMain;
	
	void initializeSetttings(EnumHorizontalPos horizontal, EnumVerticalPos vertical, boolean canOnMain) {
		this.setHorizontal(horizontal);
		this.setVertical(vertical);
		// Hidden by default: the overlay is a HUD extra, and it covering the top-right of the
		// screen unbidden is more intrusive than useful. `canOnMain` still records that the
		// overlay is allowed there, so the config option keeps working.
		this.visibleOnMain = false;
		
		this.propHorizontal = new ConfigPropertyString("Horizontal_Position", "", horizontal.name());
		this.propVertical = new ConfigPropertyString("Vertical_Position", "", vertical.name());
		this.propVisibleOnMain = new ConfigPropertyBoolean("Visible_On_Main", "", canOnMain);
		
		this.addConfigProperty(this.propHorizontal);
		this.addConfigProperty(this.propVertical);
		this.addConfigProperty(this.propVisibleOnMain);
	}
	
	@Override
	public void setupConfig(Configuration config, String category) {
		super.setupConfig(config, category);
		
		propHorizontal.setValidValues(EnumHorizontalPos.names);
		propHorizontal.setComment("Horizontal Position on the Overlay.");
		propHorizontal.setRequiresMcRestart(false);
		//propHorizontal.setLanguageKey("config.property.gui.pos.horizontal");
		
		propVertical.setValidValues(EnumVerticalPos.names);
		propVertical.setComment("Vertical Position on the Overlay.");
		propVertical.setRequiresMcRestart(false);
		//propVertical.setLanguageKey("config.property.gui.pos.vertical");
		
		propVisibleOnMain.setComment("Visibility on Main Overlay.");
		propVisibleOnMain.setRequiresMcRestart(false);
	}
	
	@Override
	public void loadFromConfig(Configuration config, String category) {
		super.loadFromConfig(config, category);
		this.setHorizontal(EnumHorizontalPos.valueOf(propHorizontal.getString()));
		this.setVertical(EnumVerticalPos.valueOf(propVertical.getString()));
		this.visibleOnMain = propVisibleOnMain.getBoolean();

		// Overlays are hidden by default now, but configs written by earlier versions have
		// Visible_On_Main=true saved and would stay on forever. Migrate once, using a marker key
		// so a player who sets them visible again is not overridden on every launch.
		if (!config.getCategory(category).containsKey(HIDDEN_BY_DEFAULT_KEY)) {
			// Both the field and the backing Property: loadFromConfig only updates the field, and
			// ConfigManager saves the Property values, so touching one alone left the file on true.
			this.visibleOnMain = false;
			propVisibleOnMain.setBoolean(false);
			propVisibleOnMain.saveToConfig();
			config.get(category, HIDDEN_BY_DEFAULT_KEY, true,
					"Internal. Set once when overlays became hidden by default.");
		}
	}

	private static final String HIDDEN_BY_DEFAULT_KEY = "Hidden_By_Default_Migrated";

	@Override
	public void saveToConfig(Configuration config, String category) {
		propHorizontal.setString(getHorizontal().name());
		propVertical.setString(getVertical().name());
		propVisibleOnMain.setBoolean(this.visibleOnMain);
		super.saveToConfig(config, category);
	}

	public EnumHorizontalPos getHorizontal() {
		return horizontal;
	}

	public void setHorizontal(EnumHorizontalPos horizontal) {
		this.horizontal = horizontal;
	}

	public EnumVerticalPos getVertical() {
		return vertical;
	}

	public void setVertical(EnumVerticalPos vertical) {
		this.vertical = vertical;
	}
	
	public boolean isVisibleOnMain() {
		return this.visibleOnMain;
	}
	
	public void setVisibleOnMain(boolean visible) {
		this.visibleOnMain = visible;
	}

}
