package stellarium.stellars.star;

import stellarapi.api.lib.math.Vector3;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.access.IStellarTessellator;
import stellarium.render.stellars.layer.LayerRenderInformation;
import stellarium.stellars.OpticsHelper;
import stellarium.stellars.render.ICelestialObjectRenderer;

public enum StarRenderer implements ICelestialObjectRenderer<StarRenderCache> {
	
	INSTANCE;

	@Override
	public void render(StarRenderCache cache, EnumStellarPass pass, LayerRenderInformation info) {
		if(!cache.shouldRender)
			return;
		info.tessellator.point(cache.appPos.getVec(), cache.red, cache.green, cache.blue);
	}

}
