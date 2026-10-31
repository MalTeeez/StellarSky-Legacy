package stellarium.render.shader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import stellarapi.api.lib.math.SpCoord;
import stellarapi.api.lib.math.Vector3;
import stellarium.StellarSky;

public class ShaderHelper {
	private static ShaderHelper instance = new ShaderHelper();
	
	public static ShaderHelper getInstance() {
		return instance;
	}
	
	private Map<String, ShaderObject> objectMap = Maps.newHashMap();
	private ShaderObject current = null;
	
	public IShaderObject buildShader(String id, ResourceLocation vertloc, ResourceLocation fragloc) {
		int vertShader = 0, fragShader = 0;
		int programObject;
		
		if(objectMap.containsKey(id)) {
			//Delete object
			GL20.glDeleteProgram(objectMap.get(id).programId);
		}
	 
		vertShader = createShader(vertloc, GL20.GL_VERTEX_SHADER);
		fragShader = createShader(fragloc, GL20.GL_FRAGMENT_SHADER);

		
		if(vertShader == 0 || fragShader == 0)
			return null;
	 
		//Creates program object
		programObject = GL20.glCreateProgram();
		
		if(programObject == 0)
			return null;
	 
		//Attatches shaders to object
		GL20.glAttachShader(programObject, vertShader);
		GL20.glAttachShader(programObject, fragShader);
		
		//Links the program
		GL20.glLinkProgram(programObject);
		
		//Check if link is done correctly
		if (GL20.glGetProgrami(programObject, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			throw new RuntimeException();
		}
	 
		/*ARBShaderObjects.glValidateProgramARB(programObject);
		if (ARBShaderObjects.glGetObjectParameteriARB(programObject, ARBShaderObjects.GL_OBJECT_VALIDATE_STATUS_ARB) == GL11.GL_FALSE) {
			throw new RuntimeException(getLogInfo(programObject));
		}*/
		
		ShaderObject object = new ShaderObject(programObject);
		objectMap.put(id, object);
		
		return object;
	}
	
	private void bindShader(ShaderObject object){		
		if(this.current != object) {
			this.releaseCurrentShader();
			this.current = object;
			
			//Use program
			GL20.glUseProgram(object.programId);
		}
	}
	
	private void releaseShader(ShaderObject object) {
		if(this.current == object)
			this.releaseCurrentShader();
	}
	
	public void releaseCurrentShader(){
		this.current = null;
		
		//Use empty program
		GL20.glUseProgram(0);
	}
	
	
	private int createShader(ResourceLocation location, int shaderType) {
		int shader = 0;
		if(location == null)
			return 0;
		
		try {
            BufferedInputStream bufferedinputstream = new BufferedInputStream(
            		Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream());
            byte[] abyte = IOUtils.toByteArray(bufferedinputstream);
            ByteBuffer bytebuffer = BufferUtils.createByteBuffer(abyte.length);
            bytebuffer.put(abyte);
            bytebuffer.position(0);
            
            //Creates the shader object
            shader = GL20.glCreateShader(shaderType);
			
			//Provide source to the shader
            GL20.glShaderSource(shader, bytebuffer);
            
            //Compiles the shader
            GL20.glCompileShader(shader);
			
			//Check compile
			if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
				throw new RuntimeException(String.format("Error creating shader %s: %s", location, GL20.glGetShaderInfoLog(shader, 32768)));
			return shader;
		}
		catch(IOException exc) {
			GL20.glDeleteShader(shader);
			throw Throwables.propagate(exc);
		}
	}
	
	private static String getLogInfo(int programObject) {
		return GL20.glGetProgramInfoLog(programObject, 32768);
	}
	
	private class ShaderObject implements IShaderObject {
		private int programId;
		private Map<String, ShaderUniform> uniformMap = Maps.newHashMap();
		
		public ShaderObject(int programId) {
			this.programId = programId;
		}

		@Override
		public void bindShader() {
			ShaderHelper.this.bindShader(this);
		}

		@Override
		public void releaseShader() {
			ShaderHelper.this.releaseShader(this);
		}

		@Override
		public IUniformField getField(String fieldName) {
			if(uniformMap.containsKey(fieldName))
				return uniformMap.get(fieldName);
			
			//Gets the location
			int location = GL20.glGetUniformLocation(this.programId, fieldName);
			if(location == -1)
				StellarSky.logger.error(String.format("Invalid field %s has claimed while loading shaders!", fieldName));
			
			ShaderUniform uniform = new ShaderUniform(location);
			uniformMap.put(fieldName, uniform);
			return uniform;
		}
	}
	
	private class ShaderUniform implements IUniformField {
		int location;
		
		public ShaderUniform(int location) {
			this.location = location;
		}

		@Override
		public void setInteger(int val) {
			GL20.glUniform1i(this.location, val);
		}

		@Override
		public void setDouble(double val) {
			GL20.glUniform1f(this.location, (float) val);
		}

		@Override
		public void setSpCoord(SpCoord val) {
			GL20.glUniform2f(this.location, (float)val.x, (float)val.y);
		}

		@Override
		public void setDouble2(double x, double y) {
			GL20.glUniform2f(this.location, (float)x, (float)y);
		}

		@Override
		public void setVector3(Vector3 val) {
			GL20.glUniform3f(this.location, (float)val.getX(), (float)val.getY(), (float)val.getZ());
		}

		@Override
		public void setDouble3(double x, double y, double z) {
			GL20.glUniform3f(this.location, (float)x, (float)y, (float)z);
		}

		@Override
		public void setDouble4(double red, double green, double blue, double alpha) {
			GL20.glUniform4f(this.location, (float)red, (float)green, (float)blue, (float)alpha);
		}
	}

}
