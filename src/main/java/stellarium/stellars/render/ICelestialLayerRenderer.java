package stellarium.stellars.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.access.IStellarTessellator;
import stellarium.render.stellars.phased.StellarChecker;
import stellarium.stellars.layer.StellarObjectContainer;
import stellarium.view.ViewerInfo;

@SideOnly(Side.CLIENT)
public interface ICelestialLayerRenderer {

	public void preRender(IStellarTessellator tessellator, EnumStellarPass pass);
	public void postRender(IStellarTessellator tessellator, EnumStellarPass pass);

	public boolean acceptPass(EnumStellarPass pass);

	/** Once per client tick, for layers that render the whole layer at once rather than per object. */
	public default void onTick(StellarObjectContainer container, ViewerInfo update, StellarChecker checker) { }

}
