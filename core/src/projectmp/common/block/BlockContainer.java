package projectmp.common.block;

import projectmp.common.tileentity.ITileEntityProvider;


public abstract class BlockContainer extends Block implements ITileEntityProvider{

	public BlockContainer(String identifier) {
		super(identifier);
	}
	
}
