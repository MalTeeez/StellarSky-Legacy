# StellarSky
Stellar Sky, Real Constellations in Minecraft!

Legacy fork for 1.7.10 with the NHGradle environment for building.
This fork backports features (mainly daytime atmospheres) from 1.12 and generally makes the mod more compatible with the rest of the 1.7 ecosystem.

## Layout

```
src/           Stellar Sky 0.1.5.7  (modid: stellarsky, package: stellarium)
stellarapi/    StellarAPI 0.1.3.8   (modid: StellarAPI,  package: stellarapi)
.upstream/     git-ignored reference clones of the upstream 1.7.10 and modern trees
```

### API
Currently stellar sky provides an api for more compatibility.

So when you are mod developer who want to make your mod compatible with mine,

Just copy the api code(stellarium.api package) into your mod, and use it however you want.
