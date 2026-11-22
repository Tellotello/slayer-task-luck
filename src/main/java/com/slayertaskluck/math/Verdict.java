package com.slayertaskluck.math;

import java.text.DecimalFormat;
import lombok.Value;

/** A plain-language luck verdict plus how rare the result is ("1 in N"). */
@Value
public class Verdict
{
	public enum Tone
	{
		UNLUCKY("unlucky"),
		NORMAL("normal"),
		LUCKY("lucky"),
		NONE("no data");

		private final String word;

		Tone(String word)
		{
			this.word = word;
		}

		public String word()
		{
			return word;
		}
	}

	public static final Verdict NONE = new Verdict(Tone.NONE, Double.NaN);

	Tone tone;
	/** Chance of a result at least this extreme on the side it falls on. */
	double tail;

	/** Verdict for a one-sided result (a dry streak can only be unlucky). */
	public static Verdict unluckySide(double tail, double unluckyCut)
	{
		return new Verdict(tail <= unluckyCut ? Tone.UNLUCKY : Tone.NORMAL, tail);
	}

	/** Verdict from a mid-p percentile (low = unlucky, high = lucky). */
	public static Verdict fromPercentile(double midP, double unluckyCut, double luckyCut)
	{
		if (midP <= unluckyCut)
		{
			return new Verdict(Tone.UNLUCKY, midP);
		}
		if (1 - midP <= luckyCut)
		{
			return new Verdict(Tone.LUCKY, 1 - midP);
		}
		return new Verdict(Tone.NORMAL, Math.min(midP, 1 - midP));
	}

	public boolean isTinted()
	{
		return tone == Tone.UNLUCKY || tone == Tone.LUCKY;
	}

	/** "1 in 16" for tinted results, "normal" otherwise. */
	public String chip()
	{
		return isTinted() ? oneIn(tail) : tone.word();
	}

	public static String oneIn(double probability)
	{
		if (!(probability > 0))
		{
			return "1 in a lot";
		}
		final double n = 1 / probability;
		if (n < 100)
		{
			return "1 in " + Math.max(1, Math.round(n));
		}
		if (n < 1_000_000)
		{
			// two significant figures keeps big numbers honest without fake precision
			final double magnitude = Math.pow(10, Math.floor(Math.log10(n)) - 1);
			return "1 in " + new DecimalFormat("#,###").format(Math.round(n / magnitude) * magnitude);
		}
		return "1 in " + new DecimalFormat("0.#").format(n / 1_000_000) + "M";
	}
}
