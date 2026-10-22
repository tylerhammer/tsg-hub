package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;

final class TsgHubBingo
{
	private TsgHubBingo()
	{
	}

	static JsonObject bestSet(JsonObject progress)
	{
		JsonObject best = null;
		for (JsonObject alt : objects(array(progress, "alternatives")))
		{
			if (best == null || share(alt) > share(best) || share(alt) == share(best) && integer(alt, "progress", 0) > integer(best, "progress", 0)) best = alt;
		}
		return best;
	}

	private static double share(JsonObject alt)
	{
		return integer(alt, "progress", 0) / (double) Math.max(1, integer(alt, "target", 1));
	}

	static String setName(JsonObject alt)
	{
		String name = TsgHubItemSets.nameFor(itemIds(array(alt, "items")));
		return name.isEmpty() ? "Set " + integer(alt, "group", 1) : name;
	}

	static List<Integer> itemIds(JsonArray items)
	{
		List<Integer> ids = new ArrayList<>();
		for (JsonObject item : objects(items)) ids.add(integer(item, "id", 0));
		return ids;
	}

	@RequiredArgsConstructor
	static final class SetLine
	{
		final String name;
		final int have;
		final int target;
		final List<String> found = new ArrayList<>();
		final List<String> finders = new ArrayList<>();
		final List<String> needed = new ArrayList<>();

		String foundText()
		{
			List<String> parts = new ArrayList<>();
			for (int i = 0; i < found.size(); i++) parts.add(finders.get(i).isEmpty() ? found.get(i) : found.get(i) + " (" + finders.get(i) + ")");
			return String.join(", ", parts);
		}

		String foundHtml(String self)
		{
			List<String> parts = new ArrayList<>();
			for (int i = 0; i < found.size(); i++)
			{
				String piece = escape(found.get(i));
				parts.add(finders.get(i).isEmpty() ? piece : piece + " (" + nameHtml(finders.get(i), self) + ")");
			}
			return String.join(", ", parts);
		}
	}

	static List<SetLine> setLines(JsonObject source, boolean withFinders)
	{
		List<JsonObject> options = objects(array(source, "alternatives"));
		if (options.isEmpty() && source.has("items"))
		{
			JsonObject single = new JsonObject();
			single.add("items", array(source, "items"));
			single.addProperty("progress", array(source, "items").size() - missingPieces(array(source, "items")).size());
			single.addProperty("target", array(source, "items").size());
			options.add(single);
		}
		options.sort((a, b) -> {
			int byShare = Double.compare(share(b), share(a));
			return byShare != 0 ? byShare : Integer.compare(integer(b, "progress", 0), integer(a, "progress", 0));
		});
		List<SetLine> lines = new ArrayList<>();
		for (JsonObject option : options)
		{
			boolean legacy = !option.has("group");
			String name = legacy ? "Pieces" : setName(option);
			SetLine line = new SetLine(name, integer(option, "progress", 0), integer(option, "target", 1));
			String prefix = legacy || name.startsWith("Set ") ? "" : name + " ";
			for (JsonObject item : objects(array(option, "items")))
			{
				String piece = str(item, "name");
				if (!prefix.isEmpty() && piece.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) piece = piece.substring(prefix.length());
				if (bool(item, "complete"))
				{
					line.found.add(piece);
					line.finders.add(withFinders ? str(item, "obtainedBy") : "");
				}
				else line.needed.add(piece);
			}
			lines.add(line);
		}
		return lines;
	}

	static List<String> missingPieces(JsonArray items)
	{
		List<String> missing = new ArrayList<>();
		for (JsonObject item : objects(items)) if (!bool(item, "complete")) missing.add(str(item, "name"));
		return missing;
	}

	static String contributorsText(JsonObject progress, String separator, int max)
	{
		return contributors(progress, separator, max, name -> name);
	}

	static String contributorsHtml(JsonObject progress, String separator, int max, String self)
	{
		return contributors(progress, separator, max, name -> nameHtml(name, self));
	}

	private static String contributors(JsonObject progress, String separator, int max, UnaryOperator<String> name)
	{
		JsonArray contributors = array(progress, "contributors");
		if (contributors.size() == 0) return "";
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < Math.min(max, contributors.size()); i++)
		{
			JsonObject entry = contributors.get(i).getAsJsonObject();
			parts.add(name.apply(str(entry, "displayName")) + " " + integer(entry, "amount", 1));
		}
		if (contributors.size() > max) parts.add("+" + (contributors.size() - max));
		return String.join(separator, parts);
	}

	static String completedLine(JsonObject progress)
	{
		String scope = str(progress, "scope");
		String by = str(progress, "completedBy");
		boolean creditedToOrganizer = creditedToOrganizer(progress);
		if (creditedToOrganizer) return "Completed (credited to no one)";
		if ("individual".equals(scope) || by.isEmpty()) return "Completed";
		if (!"solo".equals(scope) && array(progress, "contributors").size() > 0) return "Completed";
		return "Completed by " + by;
	}
}
