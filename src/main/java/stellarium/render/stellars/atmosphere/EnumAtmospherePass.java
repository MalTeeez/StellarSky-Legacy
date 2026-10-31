package stellarium.render.stellars.atmosphere;

public enum EnumAtmospherePass {
	/** Binds the sky framebuffer and sets up the refraction-adjusted view. */
	Prepare,
	/** Composites the sky to the screen through the refraction pass. */
	Finalize,
	/** Extinction over the accumulated sky, then installs the scatter renderer. */
	SetupDominateScatter,

	SetupSurfaceScatter,
	SetupPointScatter,
	SetupOpaque,
	SetupOpaqueScatter;
}
