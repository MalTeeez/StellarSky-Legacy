package stellarium.render.stellars.phased;

import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.util.ResourceLocation;
import stellarapi.api.lib.math.Matrix3;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Spmath;
import stellarapi.api.lib.math.Vector3;
import stellarapi.api.optics.EnumRGBA;
import stellarium.render.shader.IShaderObject;
import stellarium.render.stellars.access.EnumStellarPass;
import stellarium.render.stellars.access.IStellarTessellator;
import stellarium.stellars.OpticsHelper;
import stellarium.view.ViewerInfo;

public class StellarTessellator implements IStellarTessellator {

	//Limits size per draw call
	private static final int maxBufferSize = 0x200000;

	/** Dev-only: -Dstellarium.debug.pointScale=N enlarges the star quads, to test rasterisation. */
	private static final float POINT_SCALE = Float.parseFloat(
			System.getProperty("stellarium.debug.pointScale", "1"));
	
	
	private float radius;
	
	private float longitude, latitude, depth;
	private float uCoord, vCoord;
	private float red, green, blue;
	
	private float renderedRed, renderedGreen, renderedBlue;
	
	
	private boolean renderingDominate, hasTexture;
	private Vector3 xAxis, yAxis;
	private double deepDepth = 100.0;
	private IShaderObject shader;
	private ViewerInfo viewer;
	private stellarium.render.stellars.access.IDominateRenderer dominater;
	
	private float rasterizedAngleRatio;
	private double pointArea;
	
	private float weatherFactor;
	
	
	private int currentContextSize;
	
	private FloatBuffer buffer;
	private int vbo = -1;
	private float[] rawBuffer;
	private int rawBufferCount;
	private int vertexCount;
	
	
	StellarTessellator() {
		// Quads go out as two triangles each, 6/4 of the vertices.
		this.buffer = GLAllocation.createDirectFloatBuffer(maxBufferSize / 2 * 3);
		this.rawBuffer = new float[maxBufferSize];
	}
	
	public void initialize(StellarRenderInformation info) {
		this.weatherFactor = 1.0f - (1.0f - 1.0e-3f) * info.world.getRainStrength(info.partialTicks);
		
		this.shader = info.getActiveShader();
		this.viewer = info.info;

		this.dominater = info.getDominateRenderer();
		
		this.rasterizedAngleRatio = (float) (viewer.multiplyingPower / Spmath.Radians(70.0f) * info.screenSize);
		this.pointArea = info.relativeWidth * info.relativeHeight
				/ info.minecraft.displayWidth / info.minecraft.displayHeight;
		this.deepDepth = info.deepDepth;

		Entity viewerEntity = info.minecraft.renderViewEntity;
		Matrix3 transformer = new Matrix3()
				.setAsRotation(0.0, 0.0, 1.0, Math.toRadians(270.0 - viewerEntity.rotationYaw))
				.postMult(new Matrix3().setAsRotation(0.0, 1.0, 0.0,
						Math.toRadians(-viewerEntity.rotationPitch)));
		this.xAxis = transformer.transform(new Vector3(0.0,
				(double) info.relativeWidth / info.minecraft.displayWidth, 0.0));
		this.yAxis = transformer.transform(new Vector3(0.0, 0.0,
				(double) info.relativeHeight / info.minecraft.displayHeight));
		// View direction in sky coordinates: up x right. Checked against the modelview the sky is
		// drawn with. (ViewerInfo.currentDirection is a quarter turn off in azimuth in this frame.)
		this.forward = new Vector3().setCross(this.yAxis, this.xAxis).normalize();
		this.halfDiagonal = Math.atan(0.5 * Math.hypot(info.relativeWidth, info.relativeHeight));
	}

	private Vector3 forward;
	private double halfDiagonal;

	/**
	 * Whether anything within {@code angularRadius} (radians) of the unit horizontal direction can
	 * be on screen this frame. For culling small objects that would otherwise cost a draw each.
	 */
	@Override
	public boolean inView(Vector3 direction, double angularRadius) {
		double limit = this.halfDiagonal + angularRadius;
		return limit >= Math.PI || direction.dot(this.forward) >= Math.cos(limit);
	}
	
	public void initializePass(EnumStellarPass pass) {
		this.renderingDominate = pass.isDominate;
		this.hasTexture = pass.hasTexture;
	}
	
	/**
	 * Draws the star field (one static buffer, see {@link stellarium.stellars.star.StarField})
	 * with the point shader. Only the per-frame part is set here: the screen-space quad axes,
	 * scaled to the sky depth, and the colour the old per-vertex processColor applied.
	 */
	public void drawStarField(stellarium.stellars.star.StarField field) {
		if(this.renderingDominate)
			return;
		double s = POINT_SCALE * this.deepDepth;
		shader.getField("cornerX").setDouble3(xAxis.getX() * s, xAxis.getY() * s, xAxis.getZ() * s);
		shader.getField("cornerY").setDouble3(yAxis.getX() * s, yAxis.getY() * s, yAxis.getZ() * s);
		shader.getField("depth").setDouble(this.deepDepth);
		double mult = OpticsHelper.getMultFromArea(this.pointArea) * this.weatherFactor;
		shader.getField("colorScale").setDouble3(viewer.colorMultiplier.getX() * mult,
				viewer.colorMultiplier.getY() * mult, viewer.colorMultiplier.getZ() * mult);
		field.draw(this.shader);
	}

	@Override
	public void begin() {
		if(this.hasTexture) {
			this.currentContextSize = 8;
		} else {
			this.currentContextSize = 6;
			this.radius = 0.0f;
		}
		
		this.rawBufferCount = 0;
		this.vertexCount = 0;
	}

	@Override
	public void pos(SpCoord pos, float depth) {
		this.longitude = (float) Spmath.Radians(pos.x);
		this.latitude = (float) Spmath.Radians(pos.y);
		this.depth = depth;
	}

	@Override
	public void color(float red, float green, float blue) {
		this.red = red;
		this.green = green;
		this.blue = blue;
	}

	@Override
	public void texture(float u, float v) {
		if(this.hasTexture) {
			this.uCoord = u;
			this.vCoord = v;
		}
	}

	@Override
	public void writeVertex() {
		if(this.rawBufferCount + this.currentContextSize > this.rawBuffer.length)
			return; // batch full; the rest of this batch is dropped rather than overflowing

		this.processColor();

		this.rawBuffer[this.rawBufferCount] = this.longitude;
		this.rawBuffer[this.rawBufferCount + 1] = this.latitude;
		this.rawBuffer[this.rawBufferCount + 2] = this.depth;
		this.rawBuffer[this.rawBufferCount + 3] = this.renderedRed;
		this.rawBuffer[this.rawBufferCount + 4] = this.renderedGreen;
		this.rawBuffer[this.rawBufferCount + 5] = this.renderedBlue;

		if(this.hasTexture) {
			this.rawBuffer[this.rawBufferCount + 6] = this.uCoord;
			this.rawBuffer[this.rawBufferCount + 7] = this.vCoord;
		}
		this.rawBufferCount += this.currentContextSize;
		this.vertexCount ++;
	}
	
	private static final float DEFAULT_SIZE = Spmath.Radians(0.06f);

	private void processColor() {
		this.renderedRed = this.red;
		this.renderedGreen = this.green;
		this.renderedBlue = this.blue;

		if(!this.hasTexture && !this.renderingDominate) {
			// Point sources: flux spread over one pixel's solid angle,
			// the 1.12 equivalent of OpticsHelper.getMultFromArea(pointArea).
			float mult = OpticsHelper.getMultFromArea(this.pointArea);
			this.renderedRed *= viewer.colorMultiplier.getX() * mult;
			this.renderedGreen *= viewer.colorMultiplier.getY() * mult;
			this.renderedBlue *= viewer.colorMultiplier.getZ() * mult;
		} else {
			this.renderedRed *= viewer.colorMultiplier.getX();
			this.renderedGreen *= viewer.colorMultiplier.getY();
			this.renderedBlue *= viewer.colorMultiplier.getZ();
		}

		if(this.renderingDominate)
			return;

		this.renderedRed *= this.weatherFactor;
		this.renderedGreen *= this.weatherFactor;
		this.renderedBlue *= this.weatherFactor;

		// Left linear and unclamped: PostProcess owns exposure and tonemapping. The old code
		// applied a soft knee plus OpticsHelper.getCompressed() here, which clamped everything
		// to 0..1 before the HDR chain could see it.
	}

	@Override
	public void end() {
		if(this.hasTexture) {
			;
		} else if(!this.renderingDominate) {
			float size = 10.0f * (float)viewer.resolutionGeneral + 2.0f * this.radius;
			GL11.glPointSize(Math.max(this.rasterizedAngleRatio * size, 10.0f));
			if(this.radius > 0.0f)
				shader.getField("scaledRadius").setDouble(this.radius / size);
			
			// invres2 is unused: the point shaders use 1.12's min(u,1-u)*min(v,1-v) falloff.
		}
		
		if(!this.renderingDominate)
			this.tessellation();
		else if(this.dominater != null) {
			// Old code called glCallList(-1), which is a silent no-op when the atmosphere has
			// not installed a renderer; keep that tolerance instead of throwing.
			for(int index = 0; index < this.rawBufferCount; index += this.currentContextSize) {
				Vector3 lightDir = new SpCoord(Spmath.Degrees(this.rawBuffer[index]),
						Spmath.Degrees(this.rawBuffer[index + 1])).getVec();
				float red = this.rawBuffer[index + 3];
				float green = this.rawBuffer[index + 4];
				float blue = this.rawBuffer[index + 5];

				shader.getField("lightDir").setVector3(lightDir);
				shader.getField("lightColor").setDouble3(red, green, blue);
				this.dominater.renderDominate(lightDir, red, green, blue);
			}
		}
	}
	
	/**
	 * Draws the batch from our own streaming VBO. Client arrays went through Angelica's
	 * emulation - a copy into its own buffer plus a quad-to-triangle conversion per draw - which
	 * was most of what the sky's small batches cost; quads are expanded to triangles here instead.
	 */
	private void tessellation() {
		if(this.rawBufferCount <= 0)
			return;

		int size = this.currentContextSize;
		int drawn;
		buffer.clear();
		if(this.hasTexture) {
			for(int quad = 0; quad + 4 * size <= this.rawBufferCount; quad += 4 * size) {
				buffer.put(this.rawBuffer, quad, 3 * size); // 0 1 2
				buffer.put(this.rawBuffer, quad, size); // 0
				buffer.put(this.rawBuffer, quad + 2 * size, 2 * size); // 2 3
			}
			drawn = this.vertexCount / 4 * 6;
		} else {
			buffer.put(this.rawBuffer, 0, this.rawBufferCount);
			drawn = this.vertexCount;
		}
		buffer.flip();

		if(this.vbo == -1)
			this.vbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, this.buffer, GL15.GL_STREAM_DRAW);

		int stride = size << 2;
		GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0L);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glColorPointer(3, GL11.GL_FLOAT, stride, 3 * 4L);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		if(this.hasTexture) {
			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 6 * 4L);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}

		GL11.glDrawArrays(this.hasTexture? GL11.GL_TRIANGLES : GL11.GL_POINTS, 0, drawn);

		if(this.hasTexture)
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}
	
	
	@Override
	public void radius(float angularRadius) {
		if(!this.hasTexture)
			this.radius = angularRadius;
	}

	@Override
	public void bindTexture(ResourceLocation location) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(location);
	}
}
