package projectmp.common.world;

import java.util.ArrayList;

import projectmp.client.background.Background;
import projectmp.client.lighting.LightingEngine;
import projectmp.common.Main;
import projectmp.common.block.Block;
import projectmp.common.block.Blocks;
import projectmp.common.chunk.Chunk;
import projectmp.common.entity.Entity;
import projectmp.common.entity.EntityPlayer;
import projectmp.common.entity.ILoadsChunk;
import projectmp.common.packet.PacketNewEntity;
import projectmp.common.packet.PacketRemoveEntity;
import projectmp.common.packet.PacketSendTileEntity;
import projectmp.common.packet.repository.PacketRepository;
import projectmp.common.tileentity.ITileEntityProvider;
import projectmp.common.tileentity.TileEntity;
import projectmp.common.util.Particle;
import projectmp.common.util.ParticlePool;
import projectmp.common.util.QuadTree;
import projectmp.common.util.SimplexNoise;
import projectmp.common.util.Sizeable;
import projectmp.common.weather.Weather;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public class World {

	public static final int tilesizex = 32;
	public static final int tilesizey = 32;
	public static final float tilepartx = 1.0f / tilesizex;
	public static final float tileparty = 1.0f / tilesizey;

	public static final float BLOCK_RECEDE = 0.25f;

	public Main main;
	public SpriteBatch batch;

	public static final float gravity = 20f;
	public static final float drag = 20f;

	public int sizex;
	public int sizey;
	Chunk[][] chunks;
	private int[][] loadedChunks;

	public transient LightingEngine lightingEngine;

	public transient boolean isServer = false;

	public Array<Particle> particles;
	protected Array<Entity> entities;

	public QuadTree quadtree;
	private ArrayList<Entity> quadlist = new ArrayList<Entity>();

	public SimplexNoise noiseGen;
	public long seed = System.currentTimeMillis();

	public Time time = new Time();

	public Background background = new Background(this);

	private Weather weather = null;

	boolean shouldSendUpdates = true;

	/**
	 * 
	 * @param main Main instance
	 * @param x width
	 * @param y height
	 * @param server true = server-side
	 * @param seed seed
	 */
	public World(Main main, int x, int y, boolean server, long seed) {
		this.main = main;
		batch = main.batch;
		sizex = x;
		sizey = y;
		isServer = server;
		this.seed = seed;
		prepare();
	}

	public void prepare() {
		// set up chunks
		chunks = new Chunk[getWidthInChunks()][getHeightInChunks()];
		loadedChunks = new int[getWidthInChunks()][getHeightInChunks()];

		// fill in chunks
		for (int x = 0; x < getWidthInChunks(); x++) {
			for (int y = 0; y < getHeightInChunks(); y++) {
				chunks[x][y] = new Chunk(x, y);
			}
		}

		lightingEngine = new LightingEngine(this);

		for (int x = 0; x < sizex; x++) {
			for (int y = 0; y < sizey; y++) {
				setBlock(Blocks.getAir(), x, y);
				setMeta(0, x, y);
			}
		}

		entities = new Array<Entity>(32);
		particles = new Array<Particle>();
		quadtree = new QuadTree(sizex, sizey);

		noiseGen = new SimplexNoise(seed);
	}

	public void tickUpdate() {
		time.tickUpdate();

		loadChunksNearLoaders();

		// tick update loaded chunks
		for (int x = 0; x < getWidthInChunks(); x++) {
			for (int y = 0; y < getHeightInChunks(); y++) {
				if (isChunkLoaded(x, y)) {
					getChunk(x, y).tickUpdate(this);

					loadedChunks[x][y]--;
				}
			}
		}

		if (isServer) {

			for (int i = entities.size - 1; i >= 0; i--) {
				Entity e = entities.get(i);
				e.tickUpdate();

				if (e.isMarkedForRemoval()) {
					removeEntity(e.uuid);
				}
			}
		} else {

		}

		if (particles.size > 0) {
			Particle item;
			for (int i = particles.size; --i >= 0;) {
				item = particles.get(i);
				if (item.lifetime <= 0 || isServer) {
					particles.removeIndex(i);
					ParticlePool.instance().getPool().free(item);
				}
			}
		}

		quadtree.clear();
		for (int i = 0; i < entities.size; i++) {
			quadtree.insert(entities.get(i));
		}

		if (weather != null) {
			weather.tickDownTimeRemaining();

			if (weather.getTimeRemaining() > 0) {
				weather.tickUpdate();
			} else {
				weather = null;
			}
		}
	}

	public ArrayList<Entity> getQuadArea(Sizeable e) {
		quadlist.clear();
		quadtree.retrieve(quadlist, e);
		return quadlist;
	}

	public long getNewUniqueUUID() {
		long id = MathUtils.random(Long.MAX_VALUE) * MathUtils.randomSign();

		for (int i = 0; i < entities.size; i++) {
			if (entities.get(i).uuid == id) {
				return getNewUniqueUUID();
			}
		}

		return id;
	}

	public Entity getEntityByUUID(long uuid) {
		for (int i = 0; i < entities.size; i++) {
			if (entities.get(i).uuid == uuid) {
				return entities.get(i);
			}
		}

		return null;
	}

	public Entity getEntityByIndex(int i) {
		return entities.get(i);
	}

	public EntityPlayer getPlayerByUsername(String username) {
		for (int i = 0; i < entities.size; i++) {
			if (entities.get(i) instanceof EntityPlayer) {
				if (((EntityPlayer) entities.get(i)).username.equals(username)) {
					return (EntityPlayer) entities.get(i);
				}
			}
		}

		return null;
	}

	public void createNewEntity(Entity e) {
		if(e == null) return;
		
		if (isServer) {
			PacketNewEntity packet = PacketRepository.instance().newEntity;
			packet.e = e;
			main.server.sendToAllTCP(packet);
		}
		entities.add(e);
	}

	public void removeEntity(long uuid) {
		if (isServer) {
			PacketRemoveEntity packet = PacketRepository.instance().removeEntity;
			packet.uuid = uuid;
			main.server.sendToAllTCP(packet);
			entities.removeValue(getEntityByUUID(uuid), true);
		} else {
			Entity e = getEntityByUUID(uuid);
			if (e == null) return;

			// remove cursor position if client
			if (e instanceof EntityPlayer) {
				main.clientLogic.removeOtherPlayerCursor(((EntityPlayer) e).username);
			}

			entities.removeValue(e, true);
		}
	}

	public long getUUIDFromIndex(int index) {
		Entity e = getEntityByIndex(index);

		if (e != null) {
			return e.uuid;
		} else {
			return Long.MAX_VALUE;
		}
	}

	public int getNumberOfEntities() {
		return entities.size;
	}

	public void clearAllEntities() {
		for (int i = 0; i < getNumberOfEntities(); i++) {
			long uuid = getUUIDFromIndex(i);
			removeEntity(uuid);
		}
	}

	/**
	 * loads the chunks near entities that implement ILoadsChunk
	 */
	public void loadChunksNearLoaders() {
		for (int i = 0; i < entities.size; i++) {
			Entity e = entities.get(i);

			if (e instanceof ILoadsChunk) {
				int cx = getChunkX((int) e.x);
				int cy = getChunkY((int) e.y);

				for (int x = cx - 3; x < cx + 3; x++) {
					for (int y = cy - 3; y < cy + 3; y++) {
						loadChunk(x, y, Main.TICKS);
					}
				}
			}
		}
	}

	public void setWeather(Weather w) {
		weather = w;
	}

	public Weather getWeather() {
		return weather;
	}

	public int getWidthInChunks() {
		return Math.max(1, sizex / 16);
	}

	public int getHeightInChunks() {
		return Math.max(1, sizey / 16);
	}

	public Chunk getChunkBlockIsIn(int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return null;

		return chunks[getChunkX(x)][getChunkY(y)];
	}

	public Chunk getChunk(int chunkx, int chunky) {
		if (chunkx < 0 || chunky < 0 || chunkx >= getWidthInChunks()
				|| chunky >= getHeightInChunks()) return null;

		return chunks[chunkx][chunky];
	}

	/**
	 * returns the chunk the block's X coordinate is at
	 * @param blockx
	 * @return
	 */
	public int getChunkX(int blockx) {
		if (blockx < 0 || blockx >= sizex) return -1;

		return blockx / Chunk.CHUNK_SIZE;
	}

	/**
	 * returns the chunk the block's Y coordinate is at
	 * @param blocky
	 * @return
	 */
	public int getChunkY(int blocky) {
		if (blocky < 0 || blocky >= sizey) return -1;

		return blocky / Chunk.CHUNK_SIZE;
	}

	public Block getBlock(int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return Blocks.getAir();

		return getChunkBlockIsIn(x, y).getChunkBlock(x % Chunk.CHUNK_SIZE, y % Chunk.CHUNK_SIZE);
	}

	public int getMeta(int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return 0;

		return getChunkBlockIsIn(x, y).getChunkMeta(x % Chunk.CHUNK_SIZE, y % Chunk.CHUNK_SIZE);
	}

	public TileEntity getTileEntity(int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return null;

		return getChunkBlockIsIn(x, y).getChunkTileEntity(x % Chunk.CHUNK_SIZE,
				y % Chunk.CHUNK_SIZE);
	}

	public float getBreakingProgress(int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return 0;

		return getChunkBlockIsIn(x, y).getChunkBreakingProgress(x % Chunk.CHUNK_SIZE,
				y % Chunk.CHUNK_SIZE);
	}

	public void setBlock(Block b, int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return;

		if (shouldSendUpdates) getBlock(x, y).onBreak(this, x, y);

		setTileEntity(null, x, y);

		getChunkBlockIsIn(x, y).setChunkBlock(b, x % Chunk.CHUNK_SIZE, y % Chunk.CHUNK_SIZE);
		lightingEngine.scheduleLightingUpdate(true);

		if (b instanceof ITileEntityProvider) {
			setTileEntity(((ITileEntityProvider) b).createNewTileEntity(x, y), x, y);
		}

		if (shouldSendUpdates) getBlock(x, y).onPlace(this, x, y);
	}

	public void setBlock(String b, int x, int y) {
		setBlock(Blocks.instance().getBlock(b), x, y);
	}

	public void setMeta(int m, int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return;

		getChunkBlockIsIn(x, y).setChunkMeta(m, x % Chunk.CHUNK_SIZE, y % Chunk.CHUNK_SIZE);
	}

	public void setTileEntity(TileEntity te, int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return;

		getChunkBlockIsIn(x, y).setChunkTileEntity(te, x % Chunk.CHUNK_SIZE, y % Chunk.CHUNK_SIZE);
	}

	public void setBreakingProgress(int x, int y, float progress) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return;

		getChunkBlockIsIn(x, y).setChunkBreakingProgress(progress, x % Chunk.CHUNK_SIZE,
				y % Chunk.CHUNK_SIZE);
	}

	public boolean isChunkLoaded(int x, int y) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return false;

		return loadedChunks[x][y] > 0;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param length
	 * @return false if out of bounds, true otherwise
	 */
	public boolean loadChunk(int x, int y, int length) {
		if (x < 0 || y < 0 || x >= sizex || y >= sizey) return false;

		loadedChunks[x][y] = length;
		return true;
	}

	public void sendTileEntityUpdate(int x, int y) {
		PacketSendTileEntity tepacket = PacketRepository.instance().sendTileEntity;

		tepacket.te = getTileEntity(x, y);
		tepacket.x = x;
		tepacket.y = y;

		main.server.sendToAllTCP(tepacket);
	}

	public void setSendingUpdates(boolean b) {
		shouldSendUpdates = b;
	}

}
