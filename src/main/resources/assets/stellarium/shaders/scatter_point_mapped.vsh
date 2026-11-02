#version 120

// The star field (StarField): a static buffer of absolute directions and base colours,
// six vertices (two triangles) per star. Everything time-dependent happens here.

uniform vec3 toGround0, toGround1, toGround2; // absolute -> horizontal (rows)
uniform vec3 cornerX, cornerY;   // screen-space half-pixel axes, in the horizontal frame
uniform float depth;
uniform vec3 colorScale;         // scope/filter colour * point-area multiplier * weather
uniform vec3 cullScale;          // rendered iff max(colour * cullScale) > 1
uniform float leastAltitude;     // same units as the checker compares (degrees)
uniform float refraction;        // 1 when the sky refracts
uniform float twinkle;           // std-dev of the twinkle, in magnitudes
uniform float tick;

varying vec4 color;

float hash(float n) {
	return fract(sin(n) * 43758.5453);
}

void main() {
	vec3 a = gl_Vertex.xyz;
	vec3 dir = vec3(dot(toGround0, a), dot(toGround1, a), dot(toGround2, a));

	// Apparent altitude, degrees. Saemundsson as ExtinctionRefraction.refraction, with the same
	// clamp below the horizon.
	float alt = degrees(asin(clamp(dir.z, -1.0, 1.0)));
	if(refraction > 0.5) {
		float h = max(alt, -1.0);
		float newAlt = alt + 1.02 / tan(radians(h + 10.3 / (h + 5.11))) / 60.0;
		float horiz = length(dir.xy);
		if(horiz > 1.0e-6)
			dir = vec3(dir.xy * (cos(radians(newAlt)) / horiz), sin(radians(newAlt)));
		alt = newAlt;
	}

	// Twinkle: a magnitude offset, new every tick, ~gaussian (sum of three uniforms).
	float seed = gl_MultiTexCoord0.z;
	float g = (hash(seed * 12.9898 + tick * 78.233) + hash(seed * 39.3468 + tick * 11.135)
			+ hash(seed * 73.156 + tick * 52.235) - 1.5) * 2.0;
	vec3 base = gl_Color.rgb * pow(2.512, -twinkle * g);

	vec2 corner = gl_MultiTexCoord0.xy;
	if(alt < leastAltitude || max(max(base.r * cullScale.r, base.g * cullScale.g), base.b * cullScale.b) <= 1.0) {
		gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // outside the clip volume: culled
		color = vec4(0.0);
	} else {
		vec3 p = dir * depth + corner.x * cornerX + corner.y * cornerY;
		gl_Position = gl_ModelViewProjectionMatrix * vec4(p, 1.0);
		color = vec4(base * colorScale, 1.0);
	}

	gl_TexCoord[0] = vec4(corner * 0.5 + 0.5, 0.0, 1.0);
}
