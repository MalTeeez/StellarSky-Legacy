package stellarium.render.stellars.phased;

import java.nio.FloatBuffer;

import org.lwjgl.opengl.GL11;

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
	// Four vertices per point source now, so the batch is four times longer than it used to be.
	private static final int maxBufferSize = 0x200000;

	/** Dev-only: -Dstellarium.debug.logPoints=true reports how many point sources are submitted. */
	private static final boolean LOG_POINTS = Boolean.getBoolean("stellarium.debug.logPoints");

	/** Dev-only: -Dstellarium.debug.pointScale=N enlarges the point quads, to test rasterisation. */
	private static final float POINT_SCALE = Float.parseFloat(
			System.getProperty("stellarium.debug.pointScale", "1"));
	private static long lastPointLog = 0;
	
	
	private float radius;
	
	private float longitude, latitude, depth;
	private float uCoord, vCoord;
	private float red, green, blue;
	private float normalX, normalY, normalZ;
	
	private float renderedRed, renderedGreen, renderedBlue;
	
	
	private boolean renderingDominate, hasTexture, pointMode;
	private Vector3 xAxis, yAxis;
	private final Vector3 corner = new Vector3();
	private double deepDepth = 100.0;
	private IShaderObject shader;
	private ViewerInfo viewer;
	private stellarium.render.stellars.access.IDominateRenderer dominater;
	
	private float rasterizedAngleRatio;
	private double pointArea;
	
	private float weatherFactor;
	
	
	private int currentContextSize;
	private boolean hasNormal;
	
	private FloatBuffer buffer;
	private float[] rawBuffer;
	private int rawBufferCount;
	// Must not be a short: a batch is well over 32767 vertices once points are quads,
	// and a wrapped negative count is GL_INVALID_VALUE at glDrawArrays.
	private int vertexCount;
	
	
	StellarTessellator() {
		this.buffer = GLAllocation.createDirectFloatBuffer(maxBufferSize);
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
	}
	
	public void initializePass(EnumStellarPass pass) {
		this.renderingDominate = pass.isDominate;
		this.hasTexture = pass.hasTexture;
	}
	
	@Override
	public void beginPoint() {
		this.pointMode = true;
		this.hasTexture = false;
		this.radius = 0.0f;
		this.currentContextSize = 8; // position, colour, texcoord

		this.rawBufferCount = 0;
		this.vertexCount = 0;
	}

	@Override
	public void point(Vector3 direction, float red, float green, float blue) {
		// One-pixel quad around the direction, in the screen basis, so the falloff is a real
		// texture lookup rather than gl_PointCoord.
		this.color(red, green, blue);

		this.emitPointCorner(direction, +1.0, -1.0, 1.0f, 0.0f);
		this.emitPointCorner(direction, +1.0, +1.0, 1.0f, 1.0f);
		this.emitPointCorner(direction, -1.0, +1.0, 0.0f, 1.0f);
		this.emitPointCorner(direction, -1.0, -1.0, 0.0f, 0.0f);
	}

	private final Vector3 offset = new Vector3();

	private void emitPointCorner(Vector3 direction, double sx, double sy, float uCoord, float vCoord) {
		double esx = sx * POINT_SCALE;
		double esy = sy * POINT_SCALE;
		this.offset.set(this.xAxis.getX() * esx + this.yAxis.getX() * esy,
				this.xAxis.getY() * esx + this.yAxis.getY() * esy,
				this.xAxis.getZ() * esx + this.yAxis.getZ() * esy);
		this.corner.set(direction).add(this.offset);
		this.corner.normalize();

		this.longitude = (float) Math.atan2(this.corner.getY(), this.corner.getX());
		this.latitude = (float) Math.asin(Math.max(-1.0, Math.min(1.0, this.corner.getZ())));
		this.depth = (float) this.deepDepth;

		this.uCoord = uCoord;
		this.vCoord = vCoord;
		this.writeVertex();
	}

	@Override
	public void begin(boolean hasNormal) {
		this.pointMode = false;
		if(this.hasTexture) {
			this.currentContextSize = hasNormal? 11 : 8;
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
	public void normal(Vector3 normal) {
		if(this.currentContextSize == 11) {
			this.normalX = (float) normal.getX();
			this.normalY = (float) normal.getY();
			this.normalZ = (float) normal.getZ();
			this.hasNormal = true;
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

		if(this.hasTexture || this.pointMode) {
			this.rawBuffer[this.rawBufferCount + 6] = this.uCoord;
			this.rawBuffer[this.rawBufferCount + 7] = this.vCoord;
		}
		if(this.hasNormal) {
			this.rawBuffer[this.rawBufferCount + 8] = this.normalX;
			this.rawBuffer[this.rawBufferCount + 9] = this.normalY;
			this.rawBuffer[this.rawBufferCount + 10] = this.normalZ;
		}

		this.rawBufferCount += this.currentContextSize;
		this.vertexCount ++;
		this.hasNormal = false;
	}
	
	private static final float DEFAULT_SIZE = Spmath.Radians(0.06f);

	private void processColor() {
		this.renderedRed = this.red;
		this.renderedGreen = this.green;
		this.renderedBlue = this.blue;

		if((this.pointMode || !this.hasTexture) && !this.renderingDominate) {
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
		if (LOG_POINTS && this.pointMode) {
			long now = System.currentTimeMillis();
			if (now - lastPointLog > 2000) {
				lastPointLog = now;
				stellarium.StellarSky.logger.info("points: " + (this.vertexCount / 4)
						+ " quads, first colour " + this.rawBuffer[3] + "," + this.rawBuffer[4]
						+ "," + this.rawBuffer[5]
						+ " pointArea=" + this.pointArea
						+ " areaMult=" + stellarium.stellars.OpticsHelper.getMultFromArea(this.pointArea)
						+ " credit=" + this.renderedRed);
			}
		}

		if(this.hasTexture) {
			;
		} else if(!this.renderingDominate && !this.pointMode) {
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
	
	private void tessellation() {
		if(this.rawBufferCount <= 0)
			return;
		
		buffer.clear();
		buffer.put(this.rawBuffer, 0, this.rawBufferCount);
		buffer.limit(this.rawBufferCount);
		
		buffer.position(0);
		GL11.glVertexPointer(3, this.currentContextSize << 2, this.buffer);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		
		buffer.position(3);
		GL11.glColorPointer(3, this.currentContextSize << 2, this.buffer);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

		if(this.hasTexture || this.pointMode) {
			buffer.position(6);
			GL11.glTexCoordPointer(2, this.currentContextSize << 2, this.buffer);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
		
		if(this.currentContextSize == 11) {
			buffer.position(8);
			GL11.glNormalPointer(this.currentContextSize << 2, this.buffer);
			GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
		}
		
		GL11.glDrawArrays((this.hasTexture || this.pointMode)? GL11.GL_QUADS : GL11.GL_POINTS,
				0, this.vertexCount);
		
		if(this.currentContextSize == 11) {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
		
		if(this.hasTexture) {
			GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
		}
		// TODO migration to instanced rendering?
		//org.lwjgl.opengl.ARBDrawInstanced.glDrawArraysInstancedARB(mode, first, count, primcount);
		//org.lwjgl.opengl.EXTDrawInstanced.glDrawArraysInstancedEXT(mode, first, count, primcount);

		GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
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
