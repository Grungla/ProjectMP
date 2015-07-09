package projectmp.server.player;

import projectmp.common.inventory.InventoryPlayer;
import projectmp.common.inventory.itemstack.ItemStack;
import projectmp.common.io.CanBeSavedToNBT;
import projectmp.common.util.annotation.NotWrittenToNBT;
import projectmp.server.ServerLogic;

import com.evilco.mc.nbt.error.TagNotFoundException;
import com.evilco.mc.nbt.error.UnexpectedTagTypeException;
import com.evilco.mc.nbt.tag.TagCompound;
import com.evilco.mc.nbt.tag.TagFloat;

/**
 * Only used by the server instance to keep track of player position, inventory, etc. This is also why EntityPlayer implements Unsaveable.
 * 
 *
 */
public class ServerPlayer implements CanBeSavedToNBT{

	public String username = null;
	public InventoryPlayer inventory = new InventoryPlayer();
	public float posx;
	public float posy;
	
	@NotWrittenToNBT
	private ItemStack currentUsingItem = null;
	
	public ServerPlayer(String username){
		this.username = username;
	}

	public void tickUpdate(ServerLogic logic){
		if(isUsingItem()){
			currentUsingItem.getItem().onUsing(logic.world, logic.getPlayerByName(username));
		}
	}
	
	public boolean isUsingItem(){
		return currentUsingItem != null;
	}
	
	public void stopUsingItem(ServerLogic logic){
		if(!isUsingItem()) return;
		if(currentUsingItem.getItem() == null) return;
		
		currentUsingItem.getItem().onUseEnd(logic.world, logic.getPlayerByName(username));
	}
	
	public void startUsingItem(ServerLogic logic, ItemStack item){
		if(isUsingItem()) stopUsingItem(logic);
		if(item == null) return;
		if(item.getItem() == null) return;
		
		currentUsingItem = item.copy();
		currentUsingItem.getItem().onUseStart(logic.world, logic.getPlayerByName(username));
	}
	
	/**
	 * The tag's name is also the username
	 */
	@Override
	public void writeToNBT(TagCompound tag) {
		TagCompound inv = new TagCompound("Inventory");
		inventory.writeToNBT(inv);
		tag.setTag(inv);
		
		tag.setTag(new TagFloat("PosX", posx));
		tag.setTag(new TagFloat("PosY", posy));
	}

	@Override
	public void readFromNBT(TagCompound tag) throws TagNotFoundException,
			UnexpectedTagTypeException {
		inventory.readFromNBT(tag.getCompound("Inventory"));
		username = tag.getName();
		
		posx = tag.getFloat("PosX");
		posy = tag.getFloat("PosY");
	}
	
}