package projectmp.common.tileentity;

import projectmp.common.io.CanBeSavedToNBT;
import projectmp.common.world.World;

import com.evilco.mc.nbt.tag.TagCompound;

public abstract class TileEntity implements CanBeSavedToNBT {

	private transient boolean isMarkedDirty = false;
	
	protected int x = 0;
	protected int y = 0;

	public TileEntity() {

	}
	
	public TileEntity(int x, int y){
		this.x = x;
		this.y = y;
	}
	
	public void tickUpdate(World world, int x, int y){
		
	}

	@Override
	public void writeToNBT(TagCompound tag) {
	}

	@Override
	public void readFromNBT(TagCompound tag) {
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public TileEntity setLocation(int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}
	
	public void markDirty(){
		isMarkedDirty = true;
	}
	
	public boolean isDirty(){
		return isMarkedDirty;
	}
	
	public void setDirty(boolean b){
		isMarkedDirty = b;
	}

}
