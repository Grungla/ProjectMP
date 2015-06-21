package projectmp.common.chunk;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import projectmp.common.Main;
import projectmp.common.block.Block;
import projectmp.common.block.Blocks;
import projectmp.common.io.CanBeSavedToNBT;
import projectmp.common.registry.instantiator.TileEntityRegistry;
import projectmp.common.tileentity.TileEntity;

import com.evilco.mc.nbt.error.TagNotFoundException;
import com.evilco.mc.nbt.error.UnexpectedTagTypeException;
import com.evilco.mc.nbt.tag.ITag;
import com.evilco.mc.nbt.tag.TagByteArray;
import com.evilco.mc.nbt.tag.TagCompound;
import com.evilco.mc.nbt.tag.TagIntegerArray;
import com.evilco.mc.nbt.tag.TagString;

public class Chunk implements CanBeSavedToNBT {

	public static final int CHUNK_SIZE = 16;

	protected Block[][] blocks = new Block[CHUNK_SIZE][CHUNK_SIZE];
	protected byte[][] metadata = new byte[CHUNK_SIZE][CHUNK_SIZE];
	protected TileEntity[][] tileEntities = new TileEntity[CHUNK_SIZE][CHUNK_SIZE];

	int locationX = 0;
	int locationY = 0;

	public Chunk(int locx, int locy) {
		locationX = locx;
		locationY = locy;

		for (int x = 0; x < CHUNK_SIZE; x++) {
			for (int y = 0; y < CHUNK_SIZE; y++) {
				blocks[x][y] = Blocks.defaultBlock();
				metadata[x][y] = 0;
				tileEntities[x][y] = null;
			}
		}
	}

	public Block getBlock(int x, int y) {
		if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return Blocks.defaultBlock();
		if (blocks[x][y] == null) return Blocks.defaultBlock();
		return blocks[x][y];
	}

	public int getMeta(int x, int y) {
		if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return 0;
		return metadata[x][y];
	}

	public TileEntity getTileEntity(int x, int y) {
		if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return null;
		return tileEntities[x][y];
	}

	public void setBlock(Block b, int x, int y) {
		if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return;
		blocks[x][y] = b;
	}

	public void setMeta(int m, int x, int y) {
		if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return;
		metadata[x][y] = (byte) m;
	}

	public void setTileEntity(TileEntity te, int x, int y) {
		if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return;
		tileEntities[x][y] = te;
	}

	@Override
	public void writeToNBT(TagCompound tag) {
		int[] blockids = new int[CHUNK_SIZE * CHUNK_SIZE];
		byte[] metas = new byte[CHUNK_SIZE * CHUNK_SIZE];
		TagCompound tiles = new TagCompound("TileEntities");

		for (int x = 0; x < CHUNK_SIZE; x++) {
			for (int y = 0; y < CHUNK_SIZE; y++) {
				blockids[(x * CHUNK_SIZE) + y] = BlockIDMap.instance().stringToID.get(Blocks
						.instance().getKey(blocks[x][y]));
				metas[(x * CHUNK_SIZE) + y] = metadata[x][y];
				if (tileEntities[x][y] != null) {
					TagCompound teTag = new TagCompound("TileEntity_" + x + "," + y);

					teTag.setTag(new TagByteArray("Location", new byte[] { (byte) x, (byte) y }));
					teTag.setTag(new TagString("Type", TileEntityRegistry.instance().getRegistry().getKey(
							tileEntities[x][y].getClass())));

					tileEntities[x][y].writeToNBT(teTag);

					tiles.setTag(teTag);
				}
			}
		}

		tag.setTag(new TagIntegerArray("Location", new int[] { locationX, locationY }));
		tag.setTag(new TagIntegerArray("Blocks", blockids));
		tag.setTag(new TagByteArray("Metadata", metas));
		tag.setTag(tiles);
	}

	@Override
	public void readFromNBT(TagCompound tag) throws TagNotFoundException,
			UnexpectedTagTypeException {
		int[] location = null;
		int[] blockarray = null;
		byte[] metaarray = null;
		Map<String, ITag> tileEntityTags = null;

		try {
			location = tag.getIntegerArray("Location");
			blockarray = tag.getIntegerArray("Blocks");
			metaarray = tag.getByteArray("Metadata");
			tileEntityTags = tag.getCompound("TileEntities").getTags();
		} catch (UnexpectedTagTypeException | TagNotFoundException e) {
			e.printStackTrace();
		}

		locationX = location[0];
		locationY = location[1];

		for (int x = 0; x < CHUNK_SIZE; x++) {
			for (int y = 0; y < CHUNK_SIZE; y++) {
				blocks[x][y] = Blocks.instance().getBlock(
						BlockIDMap.instance().idToString.get(blockarray[(x * CHUNK_SIZE) + y]));
				metadata[x][y] = metaarray[(x * CHUNK_SIZE) + y];
				tileEntities[x][y] = null;
			}
		}

		Iterator<Entry<String, ITag>> it = tileEntityTags.entrySet().iterator();
		while (it.hasNext()) {
			TagCompound tileEntityComp = (TagCompound) it.next().getValue();

			byte[] loc = null;
			try {
				loc = tileEntityComp.getByteArray("Location");
			} catch (UnexpectedTagTypeException | TagNotFoundException e) {
				e.printStackTrace();
			}

			TileEntity te = null;
			int teLocX = loc[0] + (locationX * CHUNK_SIZE);
			int teLocY = loc[1] + (locationY * CHUNK_SIZE);
			String teType = null;
			try {
				teType = tileEntityComp.getString("Type");
				te = TileEntityRegistry.instance().getRegistry().getValue(teType).newInstance()
						.setLocation(teLocX, teLocY);
			} catch (InstantiationException | IllegalAccessException | UnexpectedTagTypeException
					| TagNotFoundException e) {
				Main.logger.warn("Failed to load tile entity at " + teLocX + ", " + teLocY
						+ " of type " + teType, e);
			}

			tileEntities[teLocX][teLocY] = te;

		}

	}

}
