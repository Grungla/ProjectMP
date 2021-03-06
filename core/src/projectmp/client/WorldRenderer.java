package projectmp.client;

import java.util.Map.Entry;

import projectmp.common.Main;
import projectmp.common.Settings;
import projectmp.common.Translator;
import projectmp.common.block.Block;
import projectmp.common.block.Blocks;
import projectmp.common.entity.Entity;
import projectmp.common.entity.EntityPlayer;
import projectmp.common.inventory.itemstack.ItemStack;
import projectmp.common.item.ItemBlock;
import projectmp.common.registry.AssetRegistry;
import projectmp.common.util.MathHelper;
import projectmp.common.util.Particle;
import projectmp.common.util.Utils;
import projectmp.common.util.render.LiquidContainer;
import projectmp.common.world.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

public class WorldRenderer implements Disposable {

	public Main main;
	public ClientLogic logic;
	public SpriteBatch batch;
	public SmoothCamera camera;
	public World world;

	private FrameBuffer worldBuffer;
	private FrameBuffer lightingBuffer;
	private FrameBuffer bypassBuffer;
	private FrameBuffer healthBuffer;

	private float seconds = 0;

	private boolean isBypassing = false;

	private float lastPlayerHealth = -1;
	private LiquidContainer healthLiquid;
	private float vignetteBloodAlpha = 0;

	private Waila waila = new Waila();

	public WorldRenderer(Main m, World w, ClientLogic l) {
		main = m;
		batch = main.batch;
		world = w;
		logic = l;

		camera = new SmoothCamera(world);

		healthLiquid = new LiquidContainer(64, 64, 2, 8, new Color(1, 0, 0, 1));

		worldBuffer = new FrameBuffer(Format.RGBA8888, Settings.DEFAULT_WIDTH,
				Settings.DEFAULT_HEIGHT, false);
		lightingBuffer = new FrameBuffer(Format.RGBA8888, Settings.DEFAULT_WIDTH,
				Settings.DEFAULT_HEIGHT, false);
		bypassBuffer = new FrameBuffer(Format.RGBA8888, Settings.DEFAULT_WIDTH,
				Settings.DEFAULT_HEIGHT, false);
		healthBuffer = new FrameBuffer(Format.RGBA8888, Settings.DEFAULT_WIDTH,
				Settings.DEFAULT_HEIGHT, false);
	}

	public void renderWorld() {
		seconds += Gdx.graphics.getDeltaTime()
				* (0.75f + ((MathHelper.clampNumberFromTime(2f) * 2) * 0.25f));

		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

		renderToLightingBuffer();

		renderToWorldBuffer();

		/* -------------------------------------------- */

		// render background
		renderBackground();

		batch.begin();

		// draw the world buffer for full light
		batch.draw(worldBuffer.getColorBufferTexture(), 0, Settings.DEFAULT_HEIGHT,
				Settings.DEFAULT_WIDTH, -Settings.DEFAULT_HEIGHT);
		if (world.getWeather() != null) {
			world.getWeather().renderOnWorld(this);
		}

		batch.flush();

		// draw the lighting buffer, masked with the world buffer texture on top of world buffer
		batch.setShader(main.maskshader);
		Main.useMask(worldBuffer.getColorBufferTexture());
		batch.draw(lightingBuffer.getColorBufferTexture(), 0, Settings.DEFAULT_HEIGHT,
				Settings.DEFAULT_WIDTH, -Settings.DEFAULT_HEIGHT);
		batch.setShader(null);

		// draw bypass buffer
		batch.draw(bypassBuffer.getColorBufferTexture(), 0, Settings.DEFAULT_HEIGHT,
				Settings.DEFAULT_WIDTH, -Settings.DEFAULT_HEIGHT);

		// draw current block
		batch.setColor(1, 1, 1, 0.05f);
		Main.fillRect(batch, convertWorldX(logic.getCursorBlockX()),
				convertWorldY(logic.getCursorBlockY(), World.tilesizex), World.tilesizex,
				World.tilesizey);
		batch.setColor(1, 1, 1, 1);

		batch.end();
	}

	private void renderBackground() {
		// render background
		batch.begin();
		batch.setColor(1, 1, 1, 1);

		world.background.render(this);

		batch.end();
	}

	private void renderToWorldBuffer() {
		// clear bypass buffer
		bypassBuffer.begin();
		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		bypassBuffer.end();

		// world to buffer
		worldBuffer.begin();

		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		batch.begin();

		// culling with 0 offsets
		int prex = getCullStartX(0);
		int prey = getCullStartY(0);
		int postx = getCullEndX(0);
		int posty = getCullEndY(0);

		boolean isSetForBreaking = false;

		int greatestRenderingLevel = 0;

		for (int layer = 0; layer <= greatestRenderingLevel; layer++) {
			for (int y = posty; y >= prey; y--) {
				for (int x = prex; x < postx; x++) {
					int blockLayer = world.getBlock(x, y).getRenderingLayer(world, x, y);

					// if the block's rendering layer is greater than the greatest so far, replace
					if (blockLayer > greatestRenderingLevel) {
						greatestRenderingLevel = blockLayer;
					}

					// render if only the rendering layer matches the current one
					if (world.getBlock(x, y).getRenderingLayer(world, x, y) == layer) {
						boolean isBeingBroken = world.getBreakingProgress(x, y) > 0;

						if (isBeingBroken) {
							if (!isSetForBreaking) {
								isSetForBreaking = true;
								batch.setShader(main.maskNoiseShader);
							}

							main.maskNoiseShader.setUniformf("time", seconds);
							main.maskNoiseShader.setUniformf("speed", 1f);
							if (x == logic.getCursorBlockX() && y == logic.getCursorBlockY()) {
								main.maskNoiseShader.setUniformf("outlinecolor", 0f, 1f, 1f, 1f);
							} else {
								main.maskNoiseShader.setUniformf("outlinecolor", 0f, 0f, 0f, 0f);
							}
							Utils.setupMaskingNoiseShader(main.maskNoiseShader,
									MathUtils.clamp(world.getBreakingProgress(x, y), 0f, 1f));
						} else {
							if (isSetForBreaking) {
								isSetForBreaking = false;
								batch.setShader(null);
							}
						}

						renderBlockInWorld(x, y);

						if (isSetForBreaking) {
							// better than re-setting the shader to default each time
							batch.flush();
						}
					}
				}
			}

			if (isSetForBreaking) {
				isSetForBreaking = false;
				batch.setShader(null);
			}

			// render entities right after layer 0
			if (layer == 0) {
				for (int i = 0; i < world.getNumberOfEntities(); i++) {
					Entity e = world.getEntityByIndex(i);

					// culling
					if (MathHelper.intersects(0, 0, Settings.DEFAULT_WIDTH,
							Settings.DEFAULT_HEIGHT, convertWorldX(e.visualX),
							convertWorldY(e.visualY, 0), e.sizex * World.tilesizex, e.sizey
									* World.tilesizey)) {
						e.render(this);
					}
				}

				// render using item
				for (Entry<String, Integer> entry : logic.getOtherPlayersSelected().entrySet()) {
					EntityPlayer p = world.getPlayerByUsername(entry.getKey());

					if (p == null) continue;
					if (MathHelper.intersects(p.x, p.y, p.sizex, p.sizey, getCullStartX(8),
							getCullStartY(8), getCullEndX(8), getCullEndY(8)) == false) continue;

					ItemStack slotItem = p.getInventoryObject().getSlot(entry.getValue());
					long cursor = logic.getOtherPlayerCursor(entry.getKey());

					if (slotItem.isNothing()) continue;

					slotItem.getItem().onUsingRender(this, p, entry.getValue(),
							Utils.unpackLongUpper(cursor), Utils.unpackLongLower(cursor));
				}

				if (logic.isUsingItem() && !logic.getSelectedItem().isNothing()) {
					logic.getSelectedItem()
							.getItem()
							.onUsingRender(this, logic.getPlayer(), logic.selectedItem,
									logic.getCursorBlockX(), logic.getCursorBlockY());
				}
			}
		}

		for (int i = 0; i < world.particles.size; i++) {
			Particle p = world.particles.get(i);

			p.render(this, main);
		}

		batch.setColor(1, 1, 1, 1);
		batch.end();

		worldBuffer.end();
	}

	private void renderToLightingBuffer() {
		// lighting to buffer
		lightingBuffer.begin();

		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		world.lightingEngine.render(this, batch);

		lightingBuffer.end();
	}

	public void renderHUD() {
		if (vignetteBloodAlpha > 0) {
			vignetteBloodAlpha -= Gdx.graphics.getDeltaTime();
		}

		if (vignetteBloodAlpha <= 0) {
			vignetteBloodAlpha = 0;
		}

		batch.begin();

		// render weather
		if (world.getWeather() != null) {
			world.getWeather().renderHUD(this);
		}

		// render vignette
		batch.setColor(0, 0, 0, 0.1f);
		batch.draw(AssetRegistry.getTexture("vignette"), 0, 0, Settings.DEFAULT_WIDTH,
				Settings.DEFAULT_HEIGHT);
		batch.setColor(1, 0, 0, vignetteBloodAlpha);
		batch.draw(AssetRegistry.getTexture("vignette"), 0, 0, Settings.DEFAULT_WIDTH,
				Settings.DEFAULT_HEIGHT);
		batch.setColor(1, 1, 1, 1);

		batch.end();

		// health liquid prep

		healthBuffer.begin();
		Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		healthLiquid.render(main, 64, 64);
		healthBuffer.end();

		batch.begin();

		batch.setColor(0, 0, 0, 0.5f);
		// heart bg
		Main.fillRect(batch, 0, 0, 86, 86);
		batch.setColor(1, 1, 1, 1);

		// 9 inventory slots
		for (int i = 0; i < 9; i++) {
			int slot = 8 - i;
			ItemStack stack = logic.getPlayerInventory().getSlot(slot);
			float spacing = 0;
			if (stack.getItem() instanceof ItemBlock) {
				spacing = 0.25f;
			}
			float posx = 0;
			float posy = 86 + (i * 64);
			float width = 64, height = 64;

			batch.setColor(1, 1, 1, 0.5f);
			batch.draw(AssetRegistry.getTexture("invslot"), posx, posy, width, height);
			batch.setColor(1, 1, 1, 1);

			main.font.setColor(1, 1, 1, 0.75f);
			main.font.draw(batch, "" + (slot + 1), posx + 2, posy + height - 2);

			if (!(stack == null || stack.isNothing())) {
				stack.getItem().render(this, posx + (spacing * width / 2),
						posy + (spacing * height / 2), width - (width * spacing),
						height - (height * spacing), stack);
				// draw number if > 1
				if (stack.getAmount() > 1) {
					float textHeight = main.font.getBounds("" + stack.getAmount()).height;
					main.drawInverse(main.font, "" + stack.getAmount(), posx + width, posy
							+ textHeight);
				}
			}

			if (slot == logic.selectedItem) {
				batch.setShader(main.maskNoiseShader);
				main.maskNoiseShader.setUniformf("speed", 5f);
				main.maskNoiseShader.setUniformf("intensity", 0.6f);
				main.maskNoiseShader.setUniformf("zoom", 50f);
				main.maskNoiseShader.setUniformf("time", seconds);

				batch.setColor(0, 0.3f, 0.5f, 0.5f);
				Main.fillRect(batch, posx, posy, width, height);
				batch.setColor(1, 1, 1, 1);

				batch.setShader(null);
			}
		}
		batch.setColor(1, 1, 1, 1);

		// START HEALTH
		float offsetX = -54f;
		float offsetY = -54f;

		Texture healthbg = AssetRegistry.getTexture("healthbg");
		batch.draw(healthbg, offsetX, Settings.DEFAULT_HEIGHT + offsetY,
				AssetRegistry.getTexture("healthbg").getWidth(),
				-AssetRegistry.getTexture("healthbg").getHeight());

		batch.flush();

		batch.setShader(main.maskshader);
		Main.useMask(AssetRegistry.getTexture("heartmask"));

		batch.draw(healthBuffer.getColorBufferTexture(), offsetX,
				Settings.DEFAULT_HEIGHT + offsetY, Settings.DEFAULT_WIDTH, -Settings.DEFAULT_HEIGHT);

		batch.setShader(null);

		// health text

		main.font.setScale(0.5f);
		main.font.setColor(0, 0, 0, 1);
		main.drawCentered(main.font,
				logic.getPlayer().health + " / " + logic.getPlayer().maxhealth, 42 + 1, 56 - 1);
		main.font.setColor(1, 1, 1, 1);
		main.drawCentered(main.font,
				logic.getPlayer().health + " / " + logic.getPlayer().maxhealth, 42, 56);
		main.font.setScale(1);

		// END HEALTH
		batch.flush();

		// START WHAT AM I LOOKING AT
		waila.render(this);

		batch.flush();

		if (logic.getCurrentGui() != null) {
			logic.getCurrentGui().render(this, logic);
		}

		batch.end();

	}

	public void renderPlayerNames() {
		batch.begin();

		// render player names
		world.main.font.setColor(1, 1, 1, 1);
		for (int i = 0; i < world.getNumberOfEntities(); i++) {
			Entity e = world.getEntityByIndex(i);

			if (e instanceof EntityPlayer) {
				if (logic.getPlayer() != e) {
					EntityPlayer p = (EntityPlayer) e;

					// culling
					if (!MathHelper.intersects(0, 0, Settings.DEFAULT_WIDTH,
							Settings.DEFAULT_HEIGHT, convertWorldX(p.visualX),
							convertWorldY(p.visualY, e.sizey * World.tilesizey), p.sizex
									* World.tilesizex, p.sizey * World.tilesizey)) continue;

					batch.setColor(1, 1, 1, 0.25f);
					world.main.drawTextBg(world.main.font, p.username, convertWorldX(p.visualX
							+ (p.sizex / 2))
							- (world.main.font.getBounds(p.username).width / 2),
							convertWorldY(p.visualY - p.sizey, World.tilesizey * p.sizey) + 20);
				}
			}
		}

		batch.end();
	}

	public void tickUpdate() {
		// took dmg
		if (lastPlayerHealth > (logic.getPlayer().health * 1f / logic.getPlayer().maxhealth)) {
			// vignette and liquid
			healthLiquid.height = 64 * (logic.getPlayer().health * 1f / logic.getPlayer().maxhealth);
			healthLiquid.perturbAll(7.5f);

			vignetteBloodAlpha = Math.abs(((lastPlayerHealth - logic.getPlayer().health * 1f
					/ logic.getPlayer().maxhealth)) * 2.5f);
		}

		// reset
		if (lastPlayerHealth != (logic.getPlayer().health * 1f / logic.getPlayer().maxhealth)) {
			lastPlayerHealth = (logic.getPlayer().health * 1f / logic.getPlayer().maxhealth);
		}
	}

	public boolean isBypassing() {
		return isBypassing;
	}

	public void startBypassing() {
		if (isBypassing) {
			throw new IllegalStateException("Cannot start bypassing buffer while already started!");
		}

		batch.flush();

		worldBuffer.end();
		bypassBuffer.begin();
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

		isBypassing = true;
	}

	public void stopBypassing() {
		if (!isBypassing) {
			throw new IllegalStateException("Cannot stop bypassing when already stopped!");
		}

		batch.flush();

		bypassBuffer.end();
		worldBuffer.begin();
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

		isBypassing = false;
	}

	public void renderBlockInWorld(int x, int y) {
		world.getBlock(x, y).renderInWorld(this, convertWorldX(x),
				convertWorldY(y, World.tilesizex), World.tilesizex, World.tilesizey, x, y);
	}

	protected void changeWorld(World w) {
		world = w;
	}

	/**
	 * Converts world coordinates to screen coordinates accounting for camera position
	 * @param worldX
	 * @return
	 */
	public float convertWorldX(float worldX) {
		return worldX * World.tilesizex - camera.camerax;
	}

	/**
	 * Converts world coordinates to screen coordinates accounting for camera position
	 * @param worldY
	 * @return
	 */
	public float convertWorldY(float worldY, float offset) {
		return Main.convertY(worldY * World.tilesizey - camera.cameray + offset);
	}

	public int getCullStartX(int extra) {
		return (int) MathUtils.clamp(((camera.camerax / World.tilesizex) - 1 - extra), 0f,
				world.sizex);
	}

	public int getCullStartY(int extra) {
		return (int) MathUtils.clamp(((camera.cameray / World.tilesizey) - 1 - extra), 0f,
				world.sizey);
	}

	public int getCullEndX(int extra) {
		return (int) MathUtils.clamp((camera.camerax / World.tilesizex) + 2 + extra
				+ (Settings.DEFAULT_WIDTH / World.tilesizex), 0f, world.sizex);
	}

	public int getCullEndY(int extra) {
		return (int) MathUtils.clamp((camera.cameray / World.tilesizey) + 2 + extra
				+ (Settings.DEFAULT_HEIGHT / World.tilesizex), 0f, world.sizey);
	}

	@Override
	public void dispose() {
		worldBuffer.dispose();
		lightingBuffer.dispose();
		bypassBuffer.dispose();
		healthBuffer.dispose();
	}

	/**
	 * What Am I Looking At
	 * 
	 *
	 */
	private static class Waila {

		private float extensionPercent = 0f;

		public void render(WorldRenderer renderer) {
			SpriteBatch batch = renderer.batch;
			Block current = renderer.world.getBlock(renderer.logic.getCursorBlockX(),
					renderer.logic.getCursorBlockY());

			renderUpdate(renderer, current);

			batch.setColor(0, 0, 0, 0.5f);
			Main.fillRect(batch, 86, 0, 192 * extensionPercent, 86);
			batch.setColor(1, 1, 1, 1);

			if (extensionPercent < 1) return;

			current.renderInWorld(renderer, 92, 8, 48, 48, renderer.logic.getCursorBlockX(),
					renderer.logic.getCursorBlockY());

			renderer.main.font.setColor(1, 1, 1, 1);

			renderer.main.font.draw(batch, current.getLocalizedName(), 92, 76);

			renderer.main.font.setScale(0.5f);
			renderer.main.font.draw(batch,
					Translator.getMsg("waila.hardness", current.getHardness()), 145, 56);
			renderer.main.font.setScale(1);
		}

		private void renderUpdate(WorldRenderer renderer, Block current) {
			if (current == null || current == Blocks.getAir()) {
				extensionPercent -= Math.max(extensionPercent * Gdx.graphics.getDeltaTime() * 8f,
						0.001f);
				if (extensionPercent < 0 || extensionPercent <= 0.01f) {
					extensionPercent = 0;
				}
			} else {
				extensionPercent += Math.max((1f - extensionPercent) * Gdx.graphics.getDeltaTime()
						* 16f, 0.001f);
				if (extensionPercent > 1 || extensionPercent >= 0.99f) {
					extensionPercent = 1f;
				}
			}
		}

	}

}
