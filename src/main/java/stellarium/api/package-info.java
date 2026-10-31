/**
 * API for Stellar Sky.
 * 3.0 changelog <p>
 *  - Added sky type, which determines acceptable render types and default config settings.
 *  - Now the renderer receives complete render handler as celestial renderer.
 * <p>
 * This package is deliberately <b>not</b> annotated with {@code @API}. FML links every mod in
 * a jar to every API that jar provides, so having StellarAPI and Stellar Sky - which now ship
 * in one jar - both provide an API makes each mod depend on the other's API and the mod sort
 * fails with a dependency cycle. Stellar Sky's own load order is expressed by its
 * {@code @Mod(dependencies)} instead.
 */
package stellarium.api;
