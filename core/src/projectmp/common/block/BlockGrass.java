package projectmp.common.block;

import projectmp.client.WorldRenderer;
import projectmp.common.Main;
import projectmp.common.world.World;

/**
 * renders one index up then the normal index
 * 
 *
 */
public class BlockGrass extends BlockFoliage {

	public BlockGrass(String identifier) {
		super(identifier);
	}

	@Override
	public void render(WorldRenderer renderer, int x, int y, float width, float height) {
		this.renderIndexAt(renderer, x, y, width, height, getCurrentRenderingIndex(renderer.world, x, y) + 1);
		super.render(renderer, x, y, width, height);
	}

}
