package com.genericpath;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("firetimer")
public interface CampfireConfig extends Config
{
	@Alpha
	@ConfigItem(
			position = 2,
			keyName = "normalTimerColor",
			name = "Normal timer color",
			description = "Configures the color of the timer"
	)
	default Color normalTimerColor()
	{
		return Color.WHITE;
	}

	@Alpha
	@ConfigItem(
			position = 3,
			keyName = "lowTimerColor",
			name = "Low timer color",
			description = "Configures the color of the timer when remaining time is low"
	)
	default Color lowTimerColor()
	{
		return Color.RED;
	}

}
