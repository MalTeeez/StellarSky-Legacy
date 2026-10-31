package stellarium.render.stellars.atmosphere;

import java.util.EnumMap;

import com.google.common.collect.Maps;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import stellarapi.api.lib.math.Vector3;
import stellarium.StellarSkyResources;
import stellarium.render.shader.IShaderObject;
import stellarium.render.shader.IUniformField;
import stellarium.render.shader.ShaderHelper;
import stellarium.render.sky.SkyRenderInformation;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.phased.StellarRenderInformation;

public class AtmShaderManager {

	private IUniformField cameraHeight;
	private IUniformField outerRadius, innerRadius;
	private IUniformField nSamples;
	private IUniformField extinctionFactor, gScattering, rayleighFactor, mieFactor;
	private IUniformField lightDir, lightColor;

	private IShaderObject extinction, refraction;
	private IUniformField refPitch, refPreRotated, refRelative;
	private IUniformField extCameraHeight, extOuterRadius, extInnerRadius;
	private IUniformField extExtinctionFactor, weatherAlpha;

	private float atmWeatherScatterFactor, atmWeatherExtFactor, atmWeatherAlpha;
	
	private EnumMap<EnumStellarPass, IShaderObject> shaderMap = Maps.newEnumMap(EnumStellarPass.class);

	public void reloadShaders() {
		shaderMap.clear();
		
		// Ray march per vertex, Rayleigh/Mie phase per pixel - 1.12's atmosphere_single.
		IShaderObject atmosphere = ShaderHelper.getInstance().buildShader("atmospheresingle",
				StellarSkyResources.vertexAtmosphereSingle, StellarSkyResources.fragmentAtmosphereSingle);
		if(atmosphere == null)
			throw new RuntimeException("Could not build the atmosphere scattering shader");
		this.injectAtmosphereHook(atmosphere);

		// Extinction is a separate, cheaper program: it integrates only the extinguished path
		// length, and multiplies the accumulated frame before the scattering is added.
		this.extinction = ShaderHelper.getInstance().buildShader("atmextinction",
				StellarSkyResources.vertexAtmExtinction, StellarSkyResources.fragmentAtmExtinction);
		if(extinction == null)
			throw new RuntimeException("Could not build the atmosphere extinction shader");
		this.extCameraHeight = extinction.getField("cameraHeight");
		this.extOuterRadius = extinction.getField("outerRadius");
		this.extInnerRadius = extinction.getField("innerRadius");
		this.extExtinctionFactor = extinction.getField("extinctionFactor");
		this.weatherAlpha = extinction.getField("weatherAlpha");

		// Refraction composites the sky framebuffer to the screen.
		this.refraction = ShaderHelper.getInstance().buildShader("atmrefraction",
				StellarSkyResources.vertexAtmRefraction, StellarSkyResources.fragmentAtmRefraction);
		if(refraction == null)
			throw new RuntimeException("Could not build the atmosphere refraction shader");
		this.refPitch = refraction.getField("pitch");
		this.refPreRotated = refraction.getField("preRotated");
		this.refRelative = refraction.getField("relative");

		// The layer shaders emit linear colour and the post chain owns exposure, so one
		// program per pass - the old mapped/skylight split is gone.
		IShaderObject scatterFuzzy = ShaderHelper.getInstance().buildShader("fuzzy",
				StellarSkyResources.vertexScatterFuzzyMapped, StellarSkyResources.fragmentScatterFuzzyMapped);

		IShaderObject scatterPoint = ShaderHelper.getInstance().buildShader("point",
				StellarSkyResources.vertexScatterPointMapped, StellarSkyResources.fragmentScatterPointMapped);

		IShaderObject srcTexture = ShaderHelper.getInstance().buildShader("srctexture",
				StellarSkyResources.vertexTextureMapped, StellarSkyResources.fragmentTextureMapped);

		shaderMap.put(EnumStellarPass.DominateScatter, atmosphere);
		shaderMap.put(EnumStellarPass.SurfaceScatter, srcTexture);
		shaderMap.put(EnumStellarPass.PointScatter, scatterPoint);
		shaderMap.put(EnumStellarPass.Opaque, srcTexture);
		shaderMap.put(EnumStellarPass.OpaqueScatter, scatterFuzzy);
	}

	private IShaderObject injectAtmosphereHook(IShaderObject shader) {
		this.cameraHeight = shader.getField("cameraHeight");
		this.outerRadius = shader.getField("outerRadius");
		this.innerRadius = shader.getField("innerRadius");
		this.nSamples = shader.getField("nSamples");

		// The scattering is integrated per light source, so these are re-set on every
		// dominate call rather than once per pass. Left unset they read as zero and the
		// whole atmosphere term vanishes.
		this.lightDir = shader.getField("lightDir");
		this.lightColor = shader.getField("lightColor");

		this.extinctionFactor = shader.getField("extinctionFactor");
		this.gScattering = shader.getField("g");
		this.rayleighFactor = shader.getField("rayleighFactor");
		this.mieFactor = shader.getField("mieFactor");
		
		return shader;
	}

	public void updateWorldInfo(StellarRenderInformation info) {
		float rainStrength = info.world.getRainStrength(info.partialTicks);
		this.atmWeatherScatterFactor = rainStrength * 0.2f;
		this.atmWeatherExtFactor = (float) (1.0f - rainStrength * 0.3f);
		this.atmWeatherExtFactor *= (1.0f - info.world.getWeightedThunderStrength(info.partialTicks) * 0.3f);
		this.atmWeatherAlpha = 1.0f - rainStrength;
	}

	/** Sets the per-source direction and brightness the scattering is integrated against. */
	public void setLightSource(Vector3 dir, float red, float green, float blue) {
		lightDir.setVector3(dir);
		lightColor.setDouble3(red, green, blue);
	}

	/** Binds the refraction program used to composite the sky to the screen. */
	public void bindRefractionShader(AtmosphereModel model, StellarRenderInformation info) {
		refraction.bindShader();
		refPitch.setDouble(Math.toRadians(-info.minecraft.renderViewEntity.rotationPitch));
		refPreRotated.setDouble(Math.toRadians(info.boundaryRefraction));
		refRelative.setDouble3(info.relativeWidth, info.relativeHeight, 1.0);
	}

	/** Binds the extinction program, which multiplies whatever is in the frame by extinction. */
	public void bindExtinctionShader(AtmosphereModel model) {
		extinction.bindShader();

		extCameraHeight.setDouble(model.getHeight());
		extOuterRadius.setDouble(model.getOuterRadius());
		extInnerRadius.setDouble(model.getInnerRadius());

		// Extinction in magnitude
		Vector3 vec = new Vector3(model.getSkyExtRed(), model.getSkyExtGreen(), model.getSkyExtBlue());
		extExtinctionFactor.setVector3(vec.scale(Math.log(10) * 0.4 - Math.log(this.atmWeatherExtFactor)));

		weatherAlpha.setDouble(this.atmWeatherAlpha);
	}
	
	/** Dev-only: -Dstellarium.debug.logAtm=true reports the scatter inputs once every 2s. */
	private static final boolean LOG_ATM = Boolean.getBoolean("stellarium.debug.logAtm");
	private static long lastAtmLog = 0;

	public IShaderObject bindShader(AtmosphereModel model, EnumStellarPass pass) {
		IShaderObject toBind = shaderMap.get(pass);
		this.setupShader(toBind, pass, model);
		return toBind;
	}
	
	private static final String defaultTexture = "texture";

	private void setupShader(IShaderObject shader, EnumStellarPass pass, AtmosphereModel model) {
		if(pass != EnumStellarPass.DominateScatter) {
			shader.bindShader();

			if(pass.hasTexture)
				shader.getField(defaultTexture).setInteger(0);
		} else {
			shader.bindShader();

			cameraHeight.setDouble(model.getHeight());
			outerRadius.setDouble(model.getOuterRadius());
			innerRadius.setDouble(model.getInnerRadius());
			nSamples.setInteger(10);

			// Extinction in magnitude
			Vector3 vec = new Vector3(model.getSkyExtRed(), model.getSkyExtGreen(), model.getSkyExtBlue());
			extinctionFactor.setVector3(vec.scale(Math.log(10) * 0.4 - Math.log(this.atmWeatherExtFactor)));
			gScattering.setDouble(-0.9 + 0.5 * this.atmWeatherScatterFactor);

			if (LOG_ATM) {
				long now = System.currentTimeMillis();
				if (now - lastAtmLog > 2000) {
					lastAtmLog = now;
					stellarium.StellarSky.logger.info("atm: height=" + model.getHeight()
							+ " inner=" + model.getInnerRadius() + " outer=" + model.getOuterRadius()
							+ " skyColor=" + model.getSkyColorRed() + "," + model.getSkyColorGreen()
							+ "," + model.getSkyColorBlue()
							+ " disp=" + model.getSkyDispRed() + "," + model.getSkyDispGreen()
							+ "," + model.getSkyDispBlue()
							+ " ext=" + model.getSkyExtRed() + "," + model.getSkyExtGreen()
							+ "," + model.getSkyExtBlue()
							+ " scatterF=" + this.atmWeatherScatterFactor
							+ " extF=" + this.atmWeatherExtFactor);
				}
			}

			double mult = 200.0;

			rayleighFactor.setDouble4(
					mult * 3 * (model.getSkyColorRed() / 0.48) * (1.0f + 8 * this.atmWeatherScatterFactor) * model.getSkyDispRed(),
					mult * 7 * (model.getSkyColorGreen() / 0.65) * (1.0f + 3 * this.atmWeatherScatterFactor) * model.getSkyDispGreen(),
					mult * 16 * model.getSkyColorBlue() * model.getSkyDispBlue(),
					1.0);

			mieFactor.setDouble4(
					mult * 0.05 * (1.0f + 20 * this.atmWeatherScatterFactor) * model.getSkyDispRed(),
					mult * 0.05 * (1.0f + 20 * this.atmWeatherScatterFactor) * model.getSkyDispGreen(),
					mult * 0.05 * (1.0f + 20 * this.atmWeatherScatterFactor) * model.getSkyDispBlue(),
					1.0);
		}
	}
}
