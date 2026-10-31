package stellarium.client;

import java.io.File;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;

public class StellarClientFMLHook {

	/**
	 * Dev-only screenshot trigger: {@code -Dstellarium.debug.screenshot=<dir>:<frames>} saves
	 * one screenshot into {@code <dir>} after the sky has rendered {@code <frames>} times.
	 * Lets automated runs verify what the sky actually looks like without any window capture.
	 */
	private static final String SCREENSHOT_PROPERTY = "stellarium.debug.screenshot";
	private final File screenshotDir;
	private final int screenshotAfter;
	/** Dev-only: -Dstellarium.debug.time=<tick> pins the world time so a run can be staged. */
	private final long forcedTime;
	/** Dev-only: -Dstellarium.debug.quit=true exits the game once the screenshot is saved. */
	private final boolean quitAfterScreenshot;
	/** Dev-only: -Dstellarium.debug.setup=true puts the player in creative, high up, for a clean sky. */
	private final boolean devSetup;
	private boolean setupDone = false;
	private static long lastGlStateLog = 0;
	/** Dev-only: -Dstellarium.debug.pitch=<deg> aims the camera; negative looks up. */
	private final float forcedPitch;
	/** Dev-only: -Dstellarium.debug.yaw=<deg> aims the camera horizontally. */
	private final float forcedYaw;
	/**
	 * Dev-only: -Dstellarium.debug.yawSpin=<deg> adds that much yaw before each screenshot, so a
	 * handful of shots sweep the sky. Bodies move with real sidereal time, so a fixed aim cannot
	 * be worked out ahead of a run.
	 */
	private final float yawSpin;
	/**
	 * Dev-only: -Dstellarium.debug.world=<name> loads that integrated world and stays in it.
	 * Owning the world lifetime here avoids racing an external orchestration mod's stage
	 * machine, which otherwise leaves the world (and presses Escape) mid-capture.
	 */
	private final String forcedWorld;
	private boolean worldRequested = false;
	/**
	 * Dev-only hard stop: quits the game after this many seconds regardless of state, so an
	 * automated run can never leave a window behind (the world-unload path can skip the
	 * screenshot-based quit entirely).
	 */
	private final long quitAfterMillis;
	private long armedAtMillis = 0;
	private boolean quitRequested = false;
	private int framesRendered = 0;
	private boolean screenshotTaken = false;
	private int shotsTaken = 0;
	private static final int MAX_SHOTS = 6;

	public StellarClientFMLHook() {
		String spec = System.getProperty(SCREENSHOT_PROPERTY);
		String dir = null;
		int after = 120;
		if (spec != null && !spec.isEmpty()) {
			int colon = spec.lastIndexOf(':');
			if (colon > 0) {
				dir = spec.substring(0, colon);
				try {
					after = Integer.parseInt(spec.substring(colon + 1));
				} catch (NumberFormatException exc) {
					after = 120;
				}
			} else {
				dir = spec;
			}
		}
		this.screenshotDir = dir == null ? null : new File(dir);
		this.screenshotAfter = after;

		long time = -1;
		String timeSpec = System.getProperty("stellarium.debug.time");
		if (timeSpec != null && !timeSpec.isEmpty()) {
			try {
				time = Long.parseLong(timeSpec) % 24000L;
			} catch (NumberFormatException exc) {
				time = -1;
			}
			if (time >= 0)
				stellarium.StellarSky.logger.info("Debug time pinned to " + time);
		}
		this.forcedTime = time;
		this.quitAfterScreenshot = Boolean.getBoolean("stellarium.debug.quit");
		this.devSetup = Boolean.getBoolean("stellarium.debug.setup");
		this.forcedPitch = Float.parseFloat(System.getProperty("stellarium.debug.pitch", "999"));
		this.forcedYaw = Float.parseFloat(System.getProperty("stellarium.debug.yaw", "999"));
		this.yawSpin = Float.parseFloat(System.getProperty("stellarium.debug.yawSpin", "0"));
		this.forcedWorld = System.getProperty("stellarium.debug.world");

		long quitAfter = Long.getLong("stellarium.debug.quitAfterSeconds", 0L);
		this.quitAfterMillis = quitAfter > 0 ? quitAfter * 1000L : 0L;
		if (this.screenshotDir != null)
			stellarium.StellarSky.logger.info("Debug screenshot armed: dir=" + this.screenshotDir
					+ " after " + after + " in-world frames");
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (this.forcedWorld != null && !this.forcedWorld.isEmpty() && !this.worldRequested
				&& event.phase == TickEvent.Phase.END) {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc.theWorld == null && mc.currentScreen != null) {
				this.worldRequested = true;
				stellarium.StellarSky.logger.info("Debug world load: " + this.forcedWorld);
				try {
					// A new world needs settings; an existing one ignores the seed fields.
					WorldSettings settings = new WorldSettings(1234567L, WorldSettings.GameType.CREATIVE,
							true, false, net.minecraft.world.WorldType.DEFAULT);
					mc.launchIntegratedServer(this.forcedWorld, this.forcedWorld, settings);
				} catch (Throwable exc) {
					// Never let dev tooling take the client down.
					stellarium.StellarSky.logger.error("Debug world load failed", exc);
				}
			}
		}

		if (this.quitAfterMillis <= 0 || this.quitRequested)
			return;

		long now = System.currentTimeMillis();
		if (this.armedAtMillis == 0)
			this.armedAtMillis = now;
		if (now - this.armedAtMillis < this.quitAfterMillis)
			return;

		this.quitRequested = true;
		stellarium.StellarSky.logger.info("Debug quit after " + (this.quitAfterMillis / 1000) + "s");
		Minecraft.getMinecraft().shutdown();
	}

	/**
	 * Render tick, end of frame: the framebuffer holds the finished image, which is the only
	 * point where a screenshot is meaningful.
	 */
	/**
	 * Dev-only: -Dstellarium.debug.logGlState=true reports the GL state the GUI is about to
	 * inherit. This has to run after the world and before the overlay, which is exactly
	 * RenderWorldLastEvent - RenderTickEvent.END is already past the crosshair.
	 */
	@SubscribeEvent
	public void onRenderWorldLast(net.minecraftforge.client.event.RenderWorldLastEvent event) {
		if (!Boolean.getBoolean("stellarium.debug.logGlState"))
			return;
		long now = System.currentTimeMillis();
		if (now - lastGlStateLog < 2000)
			return;
		lastGlStateLog = now;

		java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(4);
		org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_CURRENT_COLOR, color);
		stellarium.StellarSky.logger.info("glstate: color=" + color.get(0) + ","
				+ color.get(1) + "," + color.get(2) + "," + color.get(3)
				+ " lighting=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_LIGHTING)
				+ " blend=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND)
				+ " alphaTest=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST)
				+ " fog=" + org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_FOG));
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (this.screenshotDir == null || this.screenshotTaken || this.shotsTaken >= MAX_SHOTS
				|| event.phase != TickEvent.Phase.END)
			return;

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null)
			return;

		if (this.devSetup && !this.setupDone)
			this.setupViewpoint(mc);

		// HeadlessNH presses Escape almost immediately; dismiss it so the capture is the world.
		if (mc.currentScreen instanceof net.minecraft.client.gui.GuiIngameMenu)
			mc.displayGuiScreen(null);

		if (this.forcedPitch > -900 && mc.thePlayer != null) {
			mc.thePlayer.rotationPitch = this.forcedPitch;
			mc.thePlayer.prevRotationPitch = this.forcedPitch;
		}

		if ((this.forcedYaw > -900 || this.yawSpin != 0.0f) && mc.thePlayer != null) {
			float base = this.forcedYaw > -900 ? this.forcedYaw : this.yawSpin; // yaw irrelevant if spinning
			float yaw = base + this.shotsTaken * this.yawSpin;
			mc.thePlayer.rotationYaw = yaw;
			mc.thePlayer.prevRotationYaw = yaw;
		}

		if (this.forcedTime >= 0 && mc.theWorld.getWorldTime() % 24000L != this.forcedTime)
			mc.theWorld.setWorldTime((mc.theWorld.getWorldTime() / 24000L) * 24000L + this.forcedTime);

		// HeadlessNH's stage timing is racy, so take a series and let the caller pick a frame
		// that was captured while the world was actually up (not behind a pause menu).
		if (++this.framesRendered < this.screenshotAfter)
			return;
		if (this.framesRendered % this.screenshotAfter != 0)
			return;

		int shot = this.shotsTaken++;
		if (!this.screenshotDir.exists() && !this.screenshotDir.mkdirs())
			return;

		stellarium.StellarSky.logger.info("Taking debug screenshot " + shot + " at "
				+ mc.displayWidth + "x" + mc.displayHeight);
		ScreenShotHelper.saveScreenshot(this.screenshotDir, "shot-" + shot + ".png",
				mc.displayWidth, mc.displayHeight, mc.getFramebuffer());

		if (this.quitAfterScreenshot && shot >= MAX_SHOTS - 1) {
			// Automated runs must not leave a window behind; shut down from the next frame so
			// this one finishes cleanly.
			new Thread(() -> {
				try {
					Thread.sleep(500L);
				} catch (InterruptedException exc) {
					Thread.currentThread().interrupt();
				}
				mc.shutdown();
			}, "stellarium-debug-quit").start();
		}
	}

	/** Creative, on a block at y=199, camera at y=200 - horizon and zenith both visible. */
	private void setupViewpoint(Minecraft mc) {
		MinecraftServer server = MinecraftServer.getServer();
		if (server == null)
			return;
		EntityPlayerMP player = server.getConfigurationManager().func_152612_a(mc.getSession().getUsername());
		if (player == null)
			return;

		this.setupDone = true;
		player.setGameType(WorldSettings.GameType.CREATIVE);
		int blockX = MathHelper.floor_double(player.posX);
		int blockZ = MathHelper.floor_double(player.posZ);
		player.worldObj.setBlock(blockX, 199, blockZ, Blocks.stone);
		player.setPositionAndUpdate(blockX + 0.5, 200.0, blockZ + 0.5);
		stellarium.StellarSky.logger.info("Debug setup: creative at " + (blockX + 0.5) + ", 200, " + (blockZ + 0.5));
	}

	@SubscribeEvent
	public void onKeyInput(KeyInputEvent event) {

	}

}
