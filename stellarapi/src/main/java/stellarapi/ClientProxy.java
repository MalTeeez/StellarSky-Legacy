package stellarapi;

import java.io.IOException;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import stellarapi.api.gui.loading.ICombinedProgressUpdate;
import stellarapi.api.gui.overlay.OverlayRegistry;
import stellarapi.api.lib.config.ConfigManager;
import stellarapi.feature.gui.overlay.OverlayHandler;
import stellarapi.feature.gui.overlay.OverlaySetMain;
import stellarapi.feature.gui.overlay.OverlaySetStellarAPI;
import stellarapi.feature.gui.overlay.configurator.OverlayConfiguratorType;
import stellarapi.feature.gui.overlay.time.OverlayTimeType;

public class ClientProxy extends CommonProxy implements IProxy {
		
	private OverlayHandler overlay;
	private ConfigManager guiConfig;
	
	@Override
	public void preInit(FMLPreInitializationEvent event) {
		super.preInit(event);
		
		this.overlay = new OverlayHandler();
		
		MinecraftForge.EVENT_BUS.register(new StellarAPIClientForgeEventHook(this.overlay));
		FMLCommonHandler.instance().bus().register(new StellarAPIClientFMLEventHook(this.overlay));
		
		this.guiConfig = new ConfigManager(StellarAPI.getConfiguration(
				event.getModConfigurationDirectory(), "GuiConfig.cfg"));
		
		OverlayRegistry.registerOverlaySet("main", new OverlaySetMain());
		OverlayRegistry.registerOverlaySet("stellarapi", new OverlaySetStellarAPI());
		OverlayRegistry.registerOverlay("position", new OverlayConfiguratorType(), guiConfig);
		OverlayRegistry.registerOverlay("stellarapi.time", new OverlayTimeType(), guiConfig);
	}

	@Override
	public void load(FMLInitializationEvent event) throws IOException {
		super.load(event);
		guiConfig.syncFromFile();
	}

	@Override
	public void postInit(FMLPostInitializationEvent event) {
		super.postInit(event);		
		overlay.initialize(Minecraft.getMinecraft());
	}
	
	@Override
	public World getClientWorld() {
		return Minecraft.getMinecraft().theWorld;
	}
	
	/**
	 * StellarAPI's combined loading screen replaced vanilla's and drew its own progress list
	 * over it. Not wanted here: world-load progress is accepted and dropped, and vanilla's
	 * loading screen is left in place.
	 */
	@Override
	public ICombinedProgressUpdate getLoadingProgress() {
		return NO_LOADING_SCREEN;
	}

	private static final IProgressUpdate IGNORED_PROGRESS = new IProgressUpdate() {
		@Override public void displayProgressMessage(String message) { }
		@Override public void resetProgressAndMessage(String message) { }
		@Override public void resetProgresAndWorkingMessage(String message) { }
		@Override public void setLoadingProgress(int progress) { }
		@Override public void func_146586_a() { }
	};

	private static final ICombinedProgressUpdate NO_LOADING_SCREEN = new ICombinedProgressUpdate() {
		@Override public IProgressUpdate getProgress(String id) { return IGNORED_PROGRESS; }
		@Override public void removeProgress(String id) { }
	};
}
