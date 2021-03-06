package projectmp.common.packet;

import projectmp.client.ClientLogic;
import projectmp.common.Main;
import projectmp.common.util.Utils;
import projectmp.server.ServerLogic;

import com.esotericsoftware.kryonet.Connection;


public class PacketOtherPlayerUsing implements Packet{

	public String username;
	public boolean remove;
	public int slot;
	
	public int cursorX, cursorY;
	
	@Override
	public void actionServer(Connection connection, ServerLogic logic) {
	}

	@Override
	public void actionClient(Connection connection, ClientLogic logic) {
		if(Main.username.equals(username)){
			Main.logger.warn("Received packet for updating other players' using slot that is this client's (shouldn't happen)");
			return;
		}
		
		if(username == null) return;
		
		logic.putOtherPlayerCursor(username, Utils.packLong(cursorX, cursorY));
		
		if(remove){
			logic.removeOtherPlayerUsingItem(username);
		}else{
			logic.putOtherPlayerUsing(username, slot);
		}
		
	}

}
