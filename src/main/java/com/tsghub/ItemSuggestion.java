package com.tsghub;

final class ItemSuggestion
{
	final int id;
	final String name;

	ItemSuggestion(int id, String name) { this.id = id; this.name = name; }
	@Override public String toString() { return name; }
}
