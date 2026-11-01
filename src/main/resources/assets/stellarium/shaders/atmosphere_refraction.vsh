#version 120

// The refraction mapping is nonlinear and steep near the horizon, so it is evaluated per pixel in the fragment
// shader. Computing it only at the four corners of the screen quad and interpolating made the whole sky jump
// whenever a corner crossed the formula's pole just below the horizon.
void main() {
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
	gl_TexCoord[0] = gl_MultiTexCoord0;
}
