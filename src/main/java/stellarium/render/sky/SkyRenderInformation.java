package stellarium.render.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import stellarium.util.MCUtil;
import stellarium.view.ViewerInfo;

public class SkyRenderInformation {
	public final Minecraft minecraft;
	public final WorldClient world;
	public final Tessellator tessellator;
	public final float partialTicks;
	
	public final boolean isFrameBufferEnabled;
	public final float deepDepth;
	
	public final ViewerInfo info;
	public final double screenSize;

	/** Size of the view plane at unit distance. */
	public final double relativeWidth, relativeHeight;
	
	public SkyRenderInformation(Minecraft mc, WorldClient theWorld, float partialTicks, ViewerInfo viewer) {
		this.minecraft = mc;
		this.world = theWorld;
		this.tessellator = Tessellator.instance;
		this.partialTicks = partialTicks;
		
		this.isFrameBufferEnabled = OpenGlHelper.isFramebufferEnabled();
		
		this.deepDepth = 100.0f;

		this.info = viewer;
		this.screenSize = mc.displayWidth;

		this.relativeHeight = 2 * Math.tan(0.5 * Math.toRadians(
				MCUtil.getFOVModifier(mc.entityRenderer, partialTicks, true)));
		this.relativeWidth = (this.relativeHeight * mc.displayWidth) / mc.displayHeight;
	}
}