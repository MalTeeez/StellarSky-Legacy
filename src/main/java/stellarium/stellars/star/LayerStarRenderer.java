package stellarium.stellars.star;

import stellarium.StellarSkyResources;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.access.IStellarTessellator;
import stellarium.stellars.render.ICelestialLayerRenderer;

public enum LayerStarRenderer implements ICelestialLayerRenderer {
	
	INSTANCE;

	@Override
	public void preRender(IStellarTessellator tessellator, EnumStellarPass pass) {
		tessellator.bindTexture(StellarSkyResources.resourceStar.getLocation());
		tessellator.beginPoint();
	}

	@Override
	public void postRender(IStellarTessellator tessellator, EnumStellarPass pass) {
		tessellator.end();
	}

	@Override
	public boolean acceptPass(EnumStellarPass pass) {
		return pass == EnumStellarPass.PointScatter;
	}

}
