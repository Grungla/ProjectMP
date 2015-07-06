package projectmp.common.block;

import projectmp.client.WorldRenderer;
import projectmp.common.Main;
import projectmp.common.world.World;

public class BlockEmpty extends Block {

	public BlockEmpty(String identifier) {
		super(identifier);
	}

	public static final float DRAG = 0.7f;

	@Override
	public float getDragCoefficient(World world, int x, int y) {
		return DRAG;
	}

	/**
	 * overrode so nothing is rendered
	 */
	@Override
	public void render(WorldRenderer world, int x, int y) {

	}
}
