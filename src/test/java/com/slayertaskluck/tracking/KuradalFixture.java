package com.slayertaskluck.tracking;

import java.util.ArrayList;
import java.util.List;

/**
 * A real Kuradal task list as the game shows it, in widget order.
 * NO_TASK: opened with no active task. ON_FIRE_GIANTS: opened while on a Fire Giants task (shown as 0%).
 */
final class KuradalFixture
{
	static final String[][] NO_TASK = {
		{"Araxytes (60-80)", "0.0%"}, {"Warped Creatures (130-200)", "0.0%"}, {"Vampyres (100-210)", "0.0%"},
		{"Basilisks (130-200)", "0.0%"}, {"Drakes (50-110)", "4.9%"}, {"Wyrms (100-160)", "4.9%"},
		{"Red Dragons (30-65)", "0.0%"}, {"Lizardmen (130-210)", "0.0%"}, {"Elves (110-170)", "2.4%"},
		{"Blue Dragons (110-170)", "2.4%"}, {"Waterfiends (130-200)", "1.2%"}, {"Cave Horrors (130-200)", "2.4%"},
		{"Trolls (130-200)", "3.7%"}, {"Dust Devils (200-250)", "3.1%"}, {"Kurask (130-200)", "2.4%"},
		{"Ankou (50-80)", "3.1%"}, {"Mutated Zygomites (20-30)", "1.2%"}, {"Hellhounds (130-200)", "6.2%"},
		{"Dagannoth (130-200)", "0.0%"}, {"Fire Giants (130-200)", "4.3%"}, {"Suqahs (60-90)", "4.9%"},
		{"Skeletal Wyverns (20-40)", "4.3%"}, {"Fossil Island Wyverns (20-50)", "4.3%"},
		{"Greater Demons (200-250)", "0.0%"}, {"Black Dragons (10-20)", "0.0%"}, {"Aviansies (120-200)", "0.0%"},
		{"Spiritual Creatures (130-200)", "4.3%"}, {"Bloodveld (130-200)", "4.9%"}, {"Black Demons (200-250)", "4.9%"},
		{"Gargoyles (130-200)", "4.9%"}, {"Aberrant Spectres (130-200)", "4.3%"}, {"Cave Kraken (100-120)", "0.0%"},
		{"Smoke Devils (130-200)", "0.0%"}, {"Nechryael (200-250)", "5.5%"}, {"Kalphites (130-200)", "0.0%"},
		{"Abyssal Demons (200-250)", "7.4%"}, {"Dark Beasts (10-20)", "6.8%"}, {"Boss (Varies)", "0.0%"},
		{"Tzhaar (130-199)", "0.0%"}, {"Metal Dragons (35-45)", "0.0%"}, {"Gryphons (100-210)", "0.0%"},
		{"Aquanites (30-50)", "0.0%"}, {"Frost Dragons (70-120)", "0.0%"},
	};

	static final String[][] ON_FIRE_GIANTS = {
		{"Araxytes (60-80)", "0.0%"}, {"Warped Creatures (130-200)", "0.0%"}, {"Vampyres (100-210)", "0.0%"},
		{"Basilisks (130-200)", "0.0%"}, {"Drakes (50-110)", "5.1%"}, {"Wyrms (100-160)", "5.1%"},
		{"Red Dragons (30-65)", "0.0%"}, {"Lizardmen (130-210)", "0.0%"}, {"Elves (110-170)", "2.5%"},
		{"Blue Dragons (110-170)", "2.5%"}, {"Waterfiends (130-200)", "1.2%"}, {"Cave Horrors (130-200)", "2.5%"},
		{"Trolls (130-200)", "3.8%"}, {"Dust Devils (200-250)", "3.2%"}, {"Kurask (130-200)", "2.5%"},
		{"Ankou (50-80)", "3.2%"}, {"Mutated Zygomites (20-30)", "1.2%"}, {"Hellhounds (130-200)", "6.4%"},
		{"Dagannoth (130-200)", "0.0%"}, {"Fire Giants (130-200)", "0.0%"}, {"Suqahs (60-90)", "5.1%"},
		{"Skeletal Wyverns (20-40)", "4.5%"}, {"Fossil Island Wyverns (20-50)", "4.5%"},
		{"Greater Demons (200-250)", "0.0%"}, {"Black Dragons (10-20)", "0.0%"}, {"Aviansies (120-200)", "0.0%"},
		{"Spiritual Creatures (130-200)", "4.5%"}, {"Bloodveld (130-200)", "5.1%"}, {"Black Demons (200-250)", "5.1%"},
		{"Gargoyles (130-200)", "5.1%"}, {"Aberrant Spectres (130-200)", "4.5%"}, {"Cave Kraken (100-120)", "0.0%"},
		{"Smoke Devils (130-200)", "0.0%"}, {"Nechryael (200-250)", "5.8%"}, {"Kalphites (130-200)", "0.0%"},
		{"Abyssal Demons (200-250)", "7.7%"}, {"Dark Beasts (10-20)", "7.1%"}, {"Boss (Varies)", "0.0%"},
		{"Tzhaar (130-199)", "0.0%"}, {"Metal Dragons (35-45)", "0.0%"}, {"Gryphons (100-210)", "0.0%"},
		{"Aquanites (30-50)", "0.0%"}, {"Frost Dragons (70-120)", "0.0%"},
	};

	/** Duradel/Kuradal weights consistent with the list (total 161). */
	static int expectedWeight(String task)
	{
		switch (task)
		{
			case "Abyssal Demons":
				return 12;
			case "Dark Beasts":
				return 11;
			case "Hellhounds":
				return 10;
			case "Nechryael":
				return 9;
			case "Drakes":
			case "Wyrms":
			case "Suqahs":
			case "Bloodveld":
			case "Black Demons":
			case "Gargoyles":
				return 8;
			case "Fire Giants":
			case "Skeletal Wyverns":
			case "Fossil Island Wyverns":
			case "Spiritual Creatures":
			case "Aberrant Spectres":
				return 7;
			case "Trolls":
				return 6;
			case "Dust Devils":
			case "Ankou":
				return 5;
			case "Elves":
			case "Blue Dragons":
			case "Cave Horrors":
			case "Kurask":
				return 4;
			case "Waterfiends":
			case "Mutated Zygomites":
				return 2;
			default:
				return 0;
		}
	}

	/** Flattened widget texts, with a decoy text in between like the real interface. */
	static List<String> texts(String[][] rows)
	{
		final List<String> out = new ArrayList<>();
		out.add("Slayer Tasks");
		for (String[] row : rows)
		{
			out.add(row[0]);
			out.add("");
			out.add(row[1]);
		}
		return out;
	}

	private KuradalFixture()
	{
	}
}
