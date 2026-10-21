/*
 * Copyright (c) 2020, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tsghub;

import lombok.Value;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

@Value
class GameItem
{
	int id;
	int qty;
	String name;
	boolean stackable;

	GameItem(int id, int qty, ItemManager itemManager)
	{
		this.id = id;
		this.qty = qty;
		ItemComposition c = itemManager.getItemComposition(id);
		this.name = c.getName();
		this.stackable = c.isStackable();
	}

	static GameItem[] of(int[] packed, ItemManager itemManager)
	{
		GameItem[] out = new GameItem[packed.length / 2];
		for (int i = 0; i < out.length; i++)
			out[i] = packed[i * 2] == -1 || packed[i * 2 + 1] <= 0 ? null : new GameItem(packed[i * 2], packed[i * 2 + 1], itemManager);
		return out;
	}

	static GameItem[] of(Item[] items, ItemManager itemManager)
	{
		GameItem[] out = new GameItem[items.length];
		for (int i = 0; i < items.length; i++)
			out[i] = items[i] == null || items[i].getId() == -1 ? null : new GameItem(items[i].getId(), items[i].getQuantity(), itemManager);
		return out;
	}

	static int[] pack(GameItem[] items)
	{
		int[] out = new int[items.length * 2];
		for (int i = 0; i < items.length; i++)
		{
			out[i * 2] = items[i] == null ? -1 : items[i].id;
			out[i * 2 + 1] = items[i] == null ? 0 : items[i].qty;
		}
		return out;
	}

	String getDisplayName()
	{
		return qty <= 1 ? name : name + " x " + QuantityFormatter.formatNumber(qty);
	}
}
