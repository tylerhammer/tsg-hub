package com.tsghub.group;

public interface GroupViewSettings
{
	default boolean autoExpandMembers()
	{
		return false;
	}

	default boolean displayVirtualLevels()
	{
		return true;
	}

	default boolean displayPlayerWorlds()
	{
		return true;
	}
}
