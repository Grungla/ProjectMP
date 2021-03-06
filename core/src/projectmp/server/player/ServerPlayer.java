package projectmp.server.player;

import projectmp.common.Main;
import projectmp.common.inventory.InventoryPlayer;
import projectmp.common.inventory.itemstack.ItemStack;
import projectmp.common.io.CanBeSavedToNBT;
import projectmp.common.util.annotation.NotWrittenToNBT;
import projectmp.server.ServerLogic;

import com.evilco.mc.nbt.error.TagNotFoundException;
import com.evilco.mc.nbt.error.UnexpectedTagTypeException;
import com.evilco.mc.nbt.tag.TagCompound;
import com.evilco.mc.nbt.tag.TagFloat;
import com.evilco.mc.nbt.tag.TagLong;
import com.evilco.mc.nbt.tag.TagString;

/**
 * Only used by the server instance to keep track of player position, inventory, etc. This is also why EntityPlayer implements Unsaveable.
 * 
 *
 */
public class ServerPlayer implements CanBeSavedToNBT{

	public String username = null;
	public InventoryPlayer inventory = null;
	public float posx;
	public float posy;
	private long uuid;
	
	@NotWrittenToNBT
	private int currentUsingItem;
	
	@NotWrittenToNBT
	private boolean isUsing = false;
	
	@NotWrittenToNBT
	private int cursorX;
	@NotWrittenToNBT
	private int cursorY;
	
	public ServerPlayer(String username, long uuid){
		this.username = username;
		setUUID(uuid);
	}
	
	public void setUUID(long u){
		uuid = u;
		inventory = new InventoryPlayer(uuid);
	}
	
	public long getUUID(){
		return uuid;
	}

	public void tickUpdate(ServerLogic logic){
		if(isUsingItem()){
			getCurrentUsingItem().getItem().onUsing(logic.world, logic.getPlayerByName(username), currentUsingItem, cursorX, cursorY);
		}
	}
	
	public void setCursor(int x, int y){
		cursorX = x;
		cursorY = y;
	}
	
	public int getCursorX(){
		return cursorX;
	}
	
	public int getCursorY(){
		return cursorY;
	}
	
	public ItemStack getCurrentUsingItem(){
		return inventory.getSlot(currentUsingItem);
	}
	
	public boolean isUsingItem(){
		return getCurrentUsingItem().isNothing() == false && isUsing;
	}
	
	public void stopUsingItem(ServerLogic logic, int x, int y){
		if(!isUsingItem()) return;
		if(getCurrentUsingItem().getItem() == null) return;
		
		cursorX = x;
		cursorY = y;
		
		isUsing = false;
		
		getCurrentUsingItem().getItem().onUseEnd(logic.world, logic.getPlayerByName(username), currentUsingItem, cursorX, cursorY);
	}
	
	public void startUsingItem(ServerLogic logic, int slot, int x, int y){
		if(isUsingItem()) stopUsingItem(logic, x, y);
		if(inventory.getSlot(slot).getItem() == null) return;
		
		cursorX = x;
		cursorY = y;
		
		currentUsingItem = slot;
		isUsing = true;
		
		getCurrentUsingItem().getItem().onUseStart(logic.world, logic.getPlayerByName(username), currentUsingItem, cursorX, cursorY);
	}
	
	@Override
	public void writeToNBT(TagCompound tag) {
		TagCompound inv = new TagCompound("Inventory");
		inventory.writeToNBT(inv);
		tag.setTag(inv);
		
		tag.setTag(new TagString("Username", username));
		tag.setTag(new TagFloat("PosX", posx));
		tag.setTag(new TagFloat("PosY", posy));
		tag.setTag(new TagLong("UUID", uuid));
	}

	@Override
	public void readFromNBT(TagCompound tag) throws TagNotFoundException,
			UnexpectedTagTypeException {
		inventory.readFromNBT(tag.getCompound("Inventory"));
		username = tag.getString("Username");
		
		posx = tag.getFloat("PosX");
		posy = tag.getFloat("PosY");
		uuid = tag.getLong("UUID");
	}
	
}
