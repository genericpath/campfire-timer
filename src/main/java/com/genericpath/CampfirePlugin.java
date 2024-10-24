package com.genericpath;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
		name = "Campfire Timer"
)
public class CampfirePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CampfireOverlay campfireOverlay;

	@Getter(AccessLevel.PACKAGE)
	private long lastTrueTickUpdate;

	@Getter(AccessLevel.PACKAGE)
	private Map<Long, CampfireLocation> fireIds;

//	base 	49927
// 	red 	49928
// 	green 	49929
//	Blue	49930
// 	white 	49931
//	purp	49932

//	Firemaking animation ids for each log.. and therefore each time added to fire
//	https://github.com/runelite/runelite/blob/b3b734101942600ee2edb5aa9836636dcd7df7e1/runelite-api/src/main/java/net/runelite/api/AnimationID.java#L91C2-L101C68

//	Will then need to use the tree facing code to change the data for a given fire...
//	https://github.com/CreativeTechGuy/tree-despawn-timer/blob/527344ecf41c9b213741ae4f20fc02012f1bb945/src/main/java/com/creativetechguy/TreeDespawnTimerPlugin.java#L341C5-L341C52


	@Override
	protected void startUp() throws Exception
	{
		this.fireIds = new HashMap<>();
		this.overlayManager.add(this.campfireOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.fireIds.clear();
		this.overlayManager.remove(this.campfireOverlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
				event.getGameState() == GameState.HOPPING)
		{
			this.fireIds.clear();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned objectSpawned) {
		int temp_id = objectSpawned.getGameObject().getId();
		System.out.println(temp_id);

		if (temp_id >= 49927 && temp_id <= 49932) {
			this.fireIds.putIfAbsent(objectSpawned.getGameObject().getHash(),
					new CampfireLocation(
							objectSpawned.getGameObject(),
							objectSpawned.getGameObject().getWorldLocation(),
							0,
							this.lastTrueTickUpdate
					)
			);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned objectDespawned) {
		int temp_id = objectDespawned.getGameObject().getId();
		if (temp_id >= 49927 && temp_id <= 49932) {
			this.fireIds.remove(objectDespawned.getGameObject().getHash());
		}
	}

	@Subscribe
	public void onGameTick(GameTick change) {
		this.lastTrueTickUpdate = this.client.getTickCount();

		this.fireIds.forEach((fireIdHash, campfireLocation) ->
				campfireLocation.setTicksSinceFireLit(
						this.lastTrueTickUpdate - campfireLocation.getTickFireStarted()));
	}

	@Provides
	CampfireConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CampfireConfig.class);
	}
}
