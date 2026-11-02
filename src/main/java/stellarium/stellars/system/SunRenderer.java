package stellarium.stellars.system;

import stellarium.StellarSkyResources;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.access.IStellarTessellator;
import stellarium.render.stellars.layer.LayerRenderInformation;
import stellarium.stellars.render.ICelestialObjectRenderer;

public enum SunRenderer implements ICelestialObjectRenderer<SunRenderCache> {
	
	INSTANCE;

	private static long lastSunLog = 0;

	/** How far the halo extends past the disc, and how bright it is against the disc's 1e4. */
	private static final float SUN_HALO_SCALE = 1.8f;
	private static final float SUN_HALO_BRIGHTNESS = 90.0f;

	/** Dev-only: -Dstellarium.debug.logAtm=true reports where the sun is and how big it draws. */
	private static void stellarSkyLogPos(SunRenderCache cache) {
		stellarium.StellarSky.logger.info("sun: azimuth=" + cache.appCoord.x
				+ " altitude=" + cache.appCoord.y + " size=" + cache.size
				+ " radians (" + Math.round(Math.toDegrees(cache.size) * 2) + " deg across)");
	}

	@Override
	public void render(SunRenderCache cache, EnumStellarPass pass, LayerRenderInformation info) {
		IStellarTessellator tessellator = info.tessellator;

		if(pass == EnumStellarPass.DominateScatter) {
			tessellator.begin();
			tessellator.pos(cache.appCoord, info.deepDepth);
			tessellator.color(1.0f, 1.0f, 1.0f);
			tessellator.writeVertex();
			tessellator.end();
		} else if(pass == EnumStellarPass.OpaqueScatter) {
			// A soft halo around the disc. A limb fade on the sphere would be the cleaner way to
			// blur the edge, but the disc is drawn with flat shading and the normal-based version
			// came out constant; a radial glow costs nothing and cannot blank the sun.
			tessellator.begin();
			tessellator.radius(cache.size * SUN_HALO_SCALE);
			tessellator.pos(cache.appCoord, info.deepDepth);
			tessellator.color(SUN_HALO_BRIGHTNESS, SUN_HALO_BRIGHTNESS, SUN_HALO_BRIGHTNESS);
			tessellator.writeVertex();
			tessellator.end();
		} else if(pass == EnumStellarPass.Opaque) {
			if (Boolean.getBoolean("stellarium.debug.logAtm")) {
				long now = System.currentTimeMillis();
				if (now - lastSunLog > 2000) {
					lastSunLog = now;
					stellarSkyLogPos(cache);
				}
			}

			tessellator.bindTexture(StellarSkyResources.resourceSunSurface.getLocation());
			tessellator.begin();
			tessellator.radius(cache.size);

			float brightness = 10000.0f;
			
			int longc, latc;

			for(longc=0; longc<cache.longn; longc++){
				for(latc=0; latc<cache.latn; latc++){
					int longcd=(longc+1)%cache.longn;
					float longd=(float)longc/(float)cache.longn;
					float latd=1.0f-(float)latc/(float)cache.latn;
					float longdd=(float)(longc+1)/(float)cache.longn;
					float latdd=1.0f-(float)(latc+1)/(float)cache.latn;

					tessellator.pos(cache.sunPos[longc][latc], info.deepDepth / 2.0f);
					tessellator.texture(longd, latd);
					tessellator.color(brightness, brightness, brightness);
					tessellator.writeVertex();
					
					tessellator.pos(cache.sunPos[longcd][latc], info.deepDepth / 2.0f);
					tessellator.texture(longdd, latd);
					tessellator.color(brightness, brightness, brightness);
					tessellator.writeVertex();
					
					tessellator.pos(cache.sunPos[longcd][latc+1], info.deepDepth / 2.0f);
					tessellator.texture(longdd, latdd);
					tessellator.color(brightness, brightness, brightness);
					tessellator.writeVertex();
					
					tessellator.pos(cache.sunPos[longc][latc+1], info.deepDepth / 2.0f);
					tessellator.texture(longd, latdd);
					tessellator.color(brightness, brightness, brightness);
					tessellator.writeVertex();
				}
			}
			
			tessellator.end();
		}
	}
}