package stellarium.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.IRenderHandler;
import stellarapi.api.ICelestialCoordinate;
import stellarapi.api.ISkyEffect;
import stellarapi.api.StellarAPIReference;
import stellarapi.api.optics.IOpticalFilter;
import stellarapi.api.optics.IViewScope;
import stellarium.StellarSky;
import stellarium.lib.render.IGenericRenderer;
import stellarium.lib.render.RendererRegistry;
import stellarium.render.sky.SkyModel;
import stellarium.render.sky.SkyRenderInformation;
import stellarium.render.stellars.StellarRenderer;
import stellarium.view.ViewerInfo;

public class NewSkyRenderer extends IRenderHandler {

	private SkyModel model;
	
	public NewSkyRenderer(SkyModel model) {
		this.model = model;
	}

	@Override
	public void render(float partialTicks, WorldClient world, Minecraft mc) {
		IGenericRenderer renderer = RendererRegistry.INSTANCE.evaluateRenderer(SkyModel.class);

		EntityLivingBase viewer = mc.renderViewEntity;

		ICelestialCoordinate coordinate = StellarAPIReference.getCoordinate(world);
		ISkyEffect sky = StellarAPIReference.getSkyEffect(world);
		IViewScope scope = StellarAPIReference.getScope(viewer);
		IOpticalFilter filter = StellarAPIReference.getFilter(viewer);
		
		SkyRenderInformation info = new SkyRenderInformation(mc, world, partialTicks,
				new ViewerInfo(coordinate, sky, scope, filter, viewer));

		// Everything below renders into the HDR frame; the post chain resolves it to screen.
		StellarRenderer.INSTANCE.preRender(info);

		renderer.preRender(StellarSky.proxy.getClientSettings(), info);
		renderer.renderPass(this.model, null, info);
		renderer.postRender(StellarSky.proxy.getClientSettings(), info);

		StellarRenderer.INSTANCE.postProcess(info);

		// The sky drives per-vertex colours and leaves a warm one behind by day; vanilla draws
		// its GUI next and does not always set a colour of its own, which shows up as a tinted
		// crosshair. Clear it here, after every sky stage has run.
		GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
	}
}