package stellarium.stellars.star;

import java.nio.FloatBuffer;
import java.util.Set;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.GLAllocation;
import stellarapi.api.lib.math.Matrix3;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Vector3;
import stellarium.render.shader.IShaderObject;
import stellarium.render.stellars.phased.StellarChecker;
import stellarium.stellars.OpticsHelper;
import stellarium.stellars.layer.StellarObjectContainer;
import stellarium.stellars.util.StarColor;
import stellarium.view.ViewerInfo;

/**
 * The whole star layer as one static vertex buffer. Stars are fixed in the absolute frame, so
 * the buffer holds absolute directions and base colours, uploaded once; the point shader does
 * per tick what StarRenderCache/StarImage used to do per star on the CPU - rotation to the
 * horizon, refraction, the horizon and brightness culls, twinkling - and expands each star to
 * its screen-facing quad. CPU cost per frame is a handful of uniforms and one draw call.
 */
@SideOnly(Side.CLIENT)
public enum StarField {
	INSTANCE;

	/** abs position xyz, base colour rgb, corner xy, twinkle seed, pad. */
	private static final int FLOATS_PER_VERTEX = 10;
	private static final float[][] CORNERS = {{1, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private StellarObjectContainer builtFor;
	private int vbo = -1, vertexCount;

	// Per-tick state, read by draw().
	private StellarObjectContainer container;
	private final Vector3 row0 = new Vector3(), row1 = new Vector3(), row2 = new Vector3();
	private Vector3 cullScale = new Vector3();
	private double leastAltitude;
	private boolean refraction;
	private long tick;

	public void onTick(StellarObjectContainer container, ViewerInfo update, StellarChecker checker) {
		this.container = container;
		Matrix3 toGround = update.coordinate.getProjectionToGround();
		row0.set(toGround.getElement(0, 0), toGround.getElement(0, 1), toGround.getElement(0, 2));
		row1.set(toGround.getElement(1, 0), toGround.getElement(1, 1), toGround.getElement(1, 2));
		row2.set(toGround.getElement(2, 0), toGround.getElement(2, 1), toGround.getElement(2, 2));
		this.cullScale = checker.getPointCullScale();
		this.leastAltitude = checker.getLeastAltitudeRendered();

		// Probe instead of type-checking: the sky effect may be wrapped.
		SpCoord probe = new SpCoord(0.0, 10.0);
		update.sky.applyAtmRefraction(probe);
		this.refraction = probe.y != 10.0;

		this.tick = net.minecraft.client.Minecraft.getMinecraft().theWorld.getTotalWorldTime();
	}

	/** Draws the field with the point shader, which must be bound. */
	public void draw(IShaderObject shader) {
		if(this.container == null)
			return;
		if(this.builtFor != this.container)
			this.build(this.container);
		if(this.vertexCount == 0)
			return;

		shader.getField("toGround0").setVector3(row0);
		shader.getField("toGround1").setVector3(row1);
		shader.getField("toGround2").setVector3(row2);
		shader.getField("cullScale").setVector3(cullScale);
		shader.getField("leastAltitude").setDouble(leastAltitude);
		shader.getField("refraction").setDouble(refraction ? 1.0 : 0.0);
		shader.getField("twinkle").setDouble(OpticsHelper.getTurbulenceStd());
		// Wrapped so the float the shader gets stays exact; only needs to change per tick.
		shader.getField("tick").setDouble(this.tick % 4096L);

		int stride = FLOATS_PER_VERTEX * 4;
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vbo);
		GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0L);
		GL11.glColorPointer(3, GL11.GL_FLOAT, stride, 3 * 4L);
		GL11.glTexCoordPointer(4, GL11.GL_FLOAT, stride, 6 * 4L);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.vertexCount);

		GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}

	private void build(StellarObjectContainer container) {
		Set<BgStar> stars = container.getLoadedObjects("Star");
		FloatBuffer data = GLAllocation.createDirectFloatBuffer(stars.size() * CORNERS.length * FLOATS_PER_VERTEX);

		int seed = 0;
		for(BgStar star : stars) {
			// Twinkling is a magnitude offset, applied in the shader; this is the steady part.
			StarColor color = StarColor.getColor(star.B_V);
			double alpha = OpticsHelper.getBrightnessFromMagnitude(star.mag);
			float red = (float) (alpha * color.r / 255.0);
			float green = (float) (alpha * color.g / 255.0);
			float blue = (float) (alpha * color.b / 255.0);
			seed++;

			for(float[] corner : CORNERS) {
				data.put((float) star.pos.getX()).put((float) star.pos.getY()).put((float) star.pos.getZ());
				data.put(red).put(green).put(blue);
				data.put(corner[0]).put(corner[1]).put(seed).put(0.0f);
			}
		}
		data.flip();

		if(this.vbo == -1)
			this.vbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

		this.vertexCount = stars.size() * CORNERS.length;
		this.builtFor = container;
	}
}
