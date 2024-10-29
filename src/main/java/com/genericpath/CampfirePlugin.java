package com.genericpath;

import com.google.inject.Provides;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLOutput;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.Angle;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_ARCTIC_PINE;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_BLISTERWOOD;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_LOGS;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_MAGIC;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_MAHOGANY;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_MAPLE;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_OAK;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_REDWOOD;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_TEAK;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_WILLOW;
import static net.runelite.api.AnimationID.FIREMAKING_FORESTERS_CAMPFIRE_YEW;

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

	private ArrayList<Runnable> deferTickQueue = new ArrayList<>();


	private HashMap<WorldPoint, CampfireLocation> campfireAtLocation;
	private HashMap<WorldPoint, Integer> lastTickCampfireAtLocation;

	private HashMap<Player, CampfireLocation> playersTendingCampfires;
	private HashMap<Player, Integer> lastTickPlayersTendingCampfires;

//	base 	49927
// 	red 	49928
// 	green 	49929
//	Blue	49930
// 	white 	49931
//	purp	49932

//	https://github.com/runelite/runelite/blob/b3b734101942600ee2edb5aa9836636dcd7df7e1/runelite-api/src/main/java/net/runelite/api/AnimationID.java#L91C2-L101C68

//	Will then need to use the tree facing code to change the data for a given fire...
//	https://github.com/CreativeTechGuy/tree-despawn-timer/blob/527344ecf41c9b213741ae4f20fc02012f1bb945/src/main/java/com/creativetechguy/TreeDespawnTimerPlugin.java#L341C5-L341C52

//	TODO: logs add time.. based on log type
//	TODO: determine length of redwood log and add to wiki
//			(track the length of fires and confirm the values on wiki...)

//	Like the woodcutting one.. if there is no-one currently adding logs to a fire when it spawns
//	Then this suggests the fire was pre-existing.. display no time perhaps? since we don't know the type of log?
//	otherwise.. everytime an anim happens add the corresponding time to max of 300 ticks!

//	TODO: config for warning time
//	TODO: config for text size

	@Override
	protected void startUp() throws Exception
	{
		this.fireIds = new HashMap<>();
		this.campfireAtLocation = new HashMap<>();
		this.overlayManager.add(this.campfireOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.campfireAtLocation.clear();
		this.fireIds.clear();
		this.overlayManager.remove(this.campfireOverlay);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGGING_IN) {
			this.campfireAtLocation.clear();
			this.fireIds.clear();
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned objectSpawned) {
		int temp_id = objectSpawned.getGameObject().getId();
		if (temp_id >= 49927 && temp_id <= 49932) {
			this.fireIds.putIfAbsent(objectSpawned.getGameObject().getHash(),
					new CampfireLocation(
							objectSpawned.getGameObject(),
							objectSpawned.getGameObject().getWorldLocation(),
							0,
							this.lastTrueTickUpdate,
							0,
							0,
							0,
							0,
							new HashMap<>()
					)
			);
			this.campfireAtLocation.putIfAbsent(objectSpawned.getTile().getWorldLocation(), this.fireIds.get(objectSpawned.getGameObject().getHash()));
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "onSpawned" + objectSpawned.getTile().getWorldLocation(), null);
			// Check if any players are doing the anim here? idk
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned objectDespawned) {
		int temp_id = objectDespawned.getGameObject().getId();
		if (temp_id >= 49927 && temp_id <= 49932) {

			CampfireLocation campfire = this.fireIds.get(objectDespawned.getGameObject().getHash());
			NumberFormat format = new DecimalFormat("#");

			//	tick when fire destroyed
			int temp_last_tick = this.client.getTickCount();
			long total_time = temp_last_tick - campfire.getTickFireStarted();
			String timeLeftString = format.format(total_time);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "onDespawned: Lasted " + timeLeftString, null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "onDespawned: location " + campfire.getWorldPoint(), null);

			this.fireIds.remove(objectDespawned.getGameObject().getHash());
			this.campfireAtLocation.remove(objectDespawned.getTile().getWorldLocation());
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event) {
		if (event.getActor() instanceof Player) {
			Player player = (Player) event.getActor();
			double output = isPlayerUsingCampfire(player);
			if (output > 0) { // A player is utilising one of the campfire tending animations
				if (findClosetFacingCampfire(player) == null) {
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "onAnimation: null", null);
					return;
				}
				deferTickQueue.add(() -> this.handlePlayerFiremaking(player));
			}
		}
	}

	private void handlePlayerFiremaking(Player player) {
		double valueToAdd = isPlayerUsingCampfire(player);
		if (valueToAdd > 0) {
			System.out.println(valueToAdd);
			this.playersTendingCampfires.get(player).incrementPlayersTendingCount(player, valueToAdd);
		} else {
			this.playersTendingCampfires.remove(player);
        }

	}

	@Subscribe
	public void onGameTick(GameTick change) {
		this.lastTrueTickUpdate = this.client.getTickCount();

		this.fireIds.forEach((fireIdHash, campfireLocation) ->
				campfireLocation.setTicksSinceFireLit(
						this.lastTrueTickUpdate - campfireLocation.getTickFireStarted()));

		deferTickQueue.forEach(Runnable::run);
		deferTickQueue.clear();
	}

	@Nullable
	CampfireLocation findClosetFacingCampfire(Player player) {
		WorldPoint actorLocation = player.getWorldLocation();
		WorldPoint facingPoint = actorLocation;
		if (campfireAtLocation.get(facingPoint) == null) {
			facingPoint = neighborPoint(actorLocation, player.getOrientation());
//			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "findClosest: " + String.valueOf(facingPoint), null);
		}
		return this.campfireAtLocation.get(facingPoint);
	}

	private WorldPoint neighborPoint(WorldPoint point, int angle) {
		switch (angle) {
			case 0: // south
				return point.dy(-1);
			case 256: // southwest
				return point.dy(-1).dx(-1);
			case 512: // west
				return point.dx(-1);
			case 768: // northwest
				return point.dy(1).dx(-1);
			case 1024: // north
				return point.dy(1);
			case 1280: // northeast
				return point.dy(1).dx(1);
			case 1536: // east
				return point.dx(1);
			case 1792: // southeast
				return point.dy(-1).dx(1);

				default:
					throw new IllegalArgumentException("Invalid angle: " + angle);
		}
	}

	private double isPlayerUsingCampfire(Player player) {
		switch (player.getAnimation()) {
			case FIREMAKING_FORESTERS_CAMPFIRE_LOGS:
				return 3.3;
			case FIREMAKING_FORESTERS_CAMPFIRE_OAK:
				return 10.0;
			case FIREMAKING_FORESTERS_CAMPFIRE_WILLOW:
				return 15.0;
			case FIREMAKING_FORESTERS_CAMPFIRE_TEAK:
				return 20.0;
			case FIREMAKING_FORESTERS_CAMPFIRE_ARCTIC_PINE:
				return 21.6;
			case FIREMAKING_FORESTERS_CAMPFIRE_MAPLE:
				return 23.3;
			case FIREMAKING_FORESTERS_CAMPFIRE_MAHOGANY:
				return 26.6;
			case FIREMAKING_FORESTERS_CAMPFIRE_BLISTERWOOD:
            case FIREMAKING_FORESTERS_CAMPFIRE_YEW:
                return 33.3;
			case FIREMAKING_FORESTERS_CAMPFIRE_MAGIC:
				return 38.3;
			case FIREMAKING_FORESTERS_CAMPFIRE_REDWOOD:
				return 46.3; // todo: verify if true
            default:
					return 0.0;

		}
	}

	@Provides
	CampfireConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CampfireConfig.class);
	}
}
