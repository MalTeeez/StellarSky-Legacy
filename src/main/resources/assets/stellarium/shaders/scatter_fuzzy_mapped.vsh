#version 120

varying vec4 color;

void main() {
    float x = gl_Vertex.x;
    float y = gl_Vertex.y;
    float z = gl_Vertex.z;

    vec4 vertex = vec4(z*cos(y)*cos(x), z*cos(y)*sin(x), z*sin(y), 1);
	gl_Position = gl_ModelViewProjectionMatrix * vertex;
	color = gl_Color;
}