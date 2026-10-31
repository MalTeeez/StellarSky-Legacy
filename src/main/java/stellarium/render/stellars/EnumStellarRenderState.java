package stellarium.render.stellars;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import com.google.common.base.Function;
import com.google.common.base.Predicates;

import net.minecraft.client.renderer.RenderHelper;

import stellarium.client.ClientSettings;
import stellarium.lib.render.RendererRegistry;
import stellarium.lib.render.hierarchy.IDistributionConfigurable;
import stellarium.lib.render.hierarchy.IRenderState;
import stellarium.lib.render.hierarchy.IRenderTransition;
import stellarium.lib.render.hierarchy.IRenderedCollection;
import stellarium.render.shader.ShaderHelper;
import stellarium.render.sky.SkyRenderInformation;
import stellarium.render.stellars.StellarModel.EnumStellarRenderable;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.atmosphere.AtmosphereModel;
import stellarium.render.stellars.atmosphere.AtmosphereRenderer;
import stellarium.render.stellars.atmosphere.AtmosphereSettings;
import stellarium.render.stellars.atmosphere.EnumAtmospherePass;
import stellarium.render.stellars.phased.EnumPhasedRenderState;
import stellarium.render.stellars.phased.StellarRenderInformation;
import stellarium.util.OpenGlVersionUtil;

public enum EnumStellarRenderState implements IRenderState<Void, StellarRenderInformation> {
	Initial() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			// Same preconditions as 1.12's SkyRenderer: no lighting, no fog. The refraction
			// composite is a screen-covering quad, so leaving fog on tints the whole sky.
			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL11.GL_FOG);
			// Vanilla enables culling before this pass. The atmosphere sphere is drawn from
			// the inside, so its winding reads as back-facing and culling punches black holes
			// through the sky.
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glShadeModel(GL11.GL_SMOOTH);
			return Prepare;
		}
	},

	/**
	 * The sky framebuffer is bound from Prepare until Finalize, so the stars and the
	 * atmosphere are accumulated in the same HDR frame and share one extinction pass -
	 * the 1.12 ordering.
	 */
	Prepare() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			return SetupSurfaceScatter;
		}
	},

	SetupSurfaceScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			return RenderSurfaceScatter;
		}
	}, RenderSurfaceScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			GL11.glShadeModel(GL11.GL_FLAT);
			return SetupPointScatter;
		}
	},
	SetupPointScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			OpenGlVersionUtil.enablePointSprite();
			return RenderPointScatter;
		}
	}, RenderPointScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			OpenGlVersionUtil.disablePointSprite();
		    GL11.glDepthMask(true);
		    GL11.glEnable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glShadeModel(GL11.GL_SMOOTH);
			return SetupOpaque;
		}
	},
	SetupOpaque() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			return RenderOpaque;
		}
	}, RenderOpaque() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
		    GL11.glDepthMask(false);
		    GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
			GL11.glShadeModel(GL11.GL_FLAT);
			return SetupOpaqueScatter;
		}
	},
	SetupOpaqueScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			OpenGlVersionUtil.enablePointSprite();
			return RenderOpaqueScatter;
		}
	}, RenderOpaqueScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			OpenGlVersionUtil.disablePointSprite();
			GL11.glShadeModel(GL11.GL_SMOOTH);
			return SetupDominateScatter;
		}
	},

	SetupDominateScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			return RenderDominateScatter;
		}
	}, RenderDominateScatter() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			return Finalize;
		}
	}, Finalize() {
		@Override
		public IRenderState<Void, StellarRenderInformation> transitionTo(Void pass, StellarRenderInformation resInfo) {
			resInfo.setDominateRenderer(null);
			endRendering(resInfo);
			return null;
		}
	}
	;

	private static void endRendering(StellarRenderInformation resInfo) {
		ShaderHelper.getInstance().releaseCurrentShader();

		GL11.glEnable(GL11.GL_FOG);
		GL11.glEnable(GL11.GL_CULL_FACE);

	    GL11.glDepthMask(true);
	    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
	    GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}
	
	public static void constructRender() {
		IDistributionConfigurable<Void, SkyRenderInformation> builder = RendererRegistry.INSTANCE.configureRender(StellarModel.class);
		
		IRenderedCollection<EnumStellarRenderable> collection = builder.overallCollection();
		
		IRenderedCollection atmosphere = collection.getFiltered(Predicates.equalTo(EnumStellarRenderable.Atmosphere));
		IRenderedCollection stellar = collection.getFiltered(Predicates.equalTo(EnumStellarRenderable.Stellar));
		
		atmosphere.transformSettings(new Function<ClientSettings, AtmosphereSettings>() {
			@Override
			public AtmosphereSettings apply(ClientSettings input) {
				return (AtmosphereSettings) input.getSubConfig(AtmosphereSettings.KEY);
			}
		});

		stellar.transformSettings(new Function<ClientSettings, Void>() {
			@Override
			public Void apply(ClientSettings input) {
				return null;
			}
		});
		
		IRenderTransition transition = builder.transitionBuilder(new InfoTransformer(), Initial)
				.appendState(Prepare, EnumAtmospherePass.Prepare, atmosphere)

				.appendState(SetupSurfaceScatter, EnumAtmospherePass.SetupSurfaceScatter, atmosphere)
				.appendState(RenderSurfaceScatter, EnumStellarPass.SurfaceScatter, stellar)
				.appendState(SetupPointScatter, EnumAtmospherePass.SetupPointScatter, atmosphere)
				.appendState(RenderPointScatter, EnumStellarPass.PointScatter, stellar)
				.appendState(SetupOpaque, EnumAtmospherePass.SetupOpaque, atmosphere)
				.appendState(RenderOpaque, EnumStellarPass.Opaque, stellar)
				.appendState(SetupOpaqueScatter, EnumAtmospherePass.SetupOpaqueScatter, atmosphere)
				.appendState(RenderOpaqueScatter, EnumStellarPass.OpaqueScatter, stellar)

				.appendState(SetupDominateScatter, EnumAtmospherePass.SetupDominateScatter, atmosphere)
				.appendState(RenderDominateScatter, EnumStellarPass.DominateScatter, stellar)

				.appendState(Finalize, EnumAtmospherePass.Finalize, atmosphere)
				.build();
		
		builder.endSetup(transition);
		
		//Sub Render Constructs
		RendererRegistry.INSTANCE.bind(AtmosphereModel.class, AtmosphereRenderer.INSTANCE);
		EnumPhasedRenderState.constructRender();
	}
	
	private static class InfoTransformer implements Function<SkyRenderInformation, StellarRenderInformation> {
		@Override
		public StellarRenderInformation apply(SkyRenderInformation input) {
			return new StellarRenderInformation(input);
		}
	}
}
