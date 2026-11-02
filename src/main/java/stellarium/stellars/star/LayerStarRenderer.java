package stellarium.stellars.star;

import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.access.IStellarTessellator;
import stellarium.render.stellars.phased.StellarChecker;
import stellarium.render.stellars.phased.StellarTessellator;
import stellarium.stellars.layer.StellarObjectContainer;
import stellarium.stellars.render.ICelestialLayerRenderer;
import stellarium.view.ViewerInfo;

/** Stars have no per-object render caches; the whole layer is one {@link StarField} draw. */
public enum LayerStarRenderer implements ICelestialLayerRenderer {

	INSTANCE;

	@Override
	public void onTick(StellarObjectContainer container, ViewerInfo update, StellarChecker checker) {
		StarField.INSTANCE.onTick(container, update, checker);
	}

	@Override
	public void preRender(IStellarTessellator tessellator, EnumStellarPass pass) { }

	@Override
	public void postRender(IStellarTessellator tessellator, EnumStellarPass pass) {
		((StellarTessellator) tessellator).drawStarField(StarField.INSTANCE);
	}

	@Override
	public boolean acceptPass(EnumStellarPass pass) {
		return pass == EnumStellarPass.PointScatter;
	}

}
