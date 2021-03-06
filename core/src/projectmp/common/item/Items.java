package projectmp.common.item;

import java.util.HashMap;

import com.badlogic.gdx.utils.Array;

public class Items {

	private static Items instance;

	private Items() {
	}

	public static Items instance() {
		if (instance == null) {
			instance = new Items();
			instance.loadResources();
		}
		return instance;
	}

	private HashMap<String, Item> items = new HashMap<String, Item>();
	private HashMap<Item, String> reverse = new HashMap<Item, String>();
	private Array<Item> allItems = new Array<Item>();

	private void loadResources() {
		put("plasmaCutter", new ItemPlasmaCutter("plasmaCutter").addAnimations(Item.newSingleFrame("images/items/mininglaser.png")));
		put("scrapMetal", new ItemScrapMetal("scrapMetal").addAnimations(Item.newSingleFrame("images/items/scrapMetal.png")));
	}

	/**
	 * 
	 * @param key the item's unique key, usually also the unlocalized name if the item is unique
	 * @param value
	 */
	public void put(String key, Item value) {
		items.put(key, value);
		reverse.put(value, key);
		allItems.add(value);
	}

	public Item getItem(String key) {
		if (key == null) return null;
		return items.get(key);
	}

	public String getKey(Item item) {
		if (item == null) return null;
		return reverse.get(item);
	}

	public Array<Item> getItemList() {
		return allItems;
	}

}
