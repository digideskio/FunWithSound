package io.github.daveho.funwithsound;

/**
 * Parameter names for components that take configuration from
 * DataBeads.
 */
public interface ParamNames {
	// Used by MonoSynthUGen2
	/** DataBead property name: Glide time between notes (for portamento). */
	public static final String GLIDE_TIME_MS = "glideTimeMs";
	
	// Used by ASRNoteEnvelope
	/** DataBead property name: Time to ramp up to full gain when note starts. */
	public static final String ATTACK_TIME_MS = "attackTimeMs";
	/** DataBead property name: Time to decay to silence when note ends. */
	public static final String RELEASE_TIME_MS = "releaseTimeMs";
	/** DataBead property name: Minimum gain (for notes with velocity 0.) */
	public static final String MIN_GAIN = "minGain";
	
	// Used by RingModulationVoice and FMVoice
	/** DataBead property name: The multiple of the note frequency that should be used to generate the modulator frequency. */
	public static final String MOD_FREQ_MULTIPLE = "modFreqMultiple";
	/** DataBead property name: Glide time for changes in the modulation frequency. */
	public static final String MOD_GLIDE_TIME_MS = "modGlideTimeMs";

	// Used by FMVoice
	/** DataBead property name: Minimum carrier frequency as multiple of base note frequency. */
	public static final String MIN_FREQ_MULTIPLE = "minFreqMultiple";
	/** DataBead property name: Maximum carrier frequency as multiple of base note frequency. */
	public static final String MAX_FREQ_MULTIPLE = "maxFreqMultiple";

	// Used by BandpassFilterNoteEnvelopeAdapter 
	/** DataBead property name: Start frequency (expressed as a multiple of the note frequency). */
	public static final String START_END_FREQ_FACTOR = "startEndFreqFactor";
	/** DataBead property name: Rise frequency (expressed as a multiple of the note frequency). */
	public static final String RISE_FREQ_FACTOR = "riseFreqFactor";
	/** DataBead property name: Time to rise from the start frequency to the rise frequency. */
	public static final String RISE_TIME_MS = "riseTimeMs";
	/** DataBead property name: Time to decay from the rise frequency back to the start frequency. */
	public static final String FALL_TIME_MS = "fallTimeMs";
	/** DataBead property name: Curvature of the glides from start to rise and back. */
	public static final String CURVATURE = "curvature";
	
	// User by AddReverb
	/** DataBead property name: Late reverb level, in the range 0-1. */
	public static final String LATE_REVERB_LEVEL = "lateReverbLevel";
	/** DataBead property name: Early reflections level, in the range 0-1. */
	public static final String EARLY_REFLECTIONS_LEVEL = "earlyReflectionsLevel";
	/** DataBead property name: Room size, in the range 0-1. */
	public static final String ROOM_SIZE = "roomSize";
	/** DataBead property name: Damping, in the range 0-1. */
	public static final String DAMPING = "damping";
	
	// Used by AddPingPongStereoDelays
	/** DataBead property name: Number of delays. */
	public static final String NUM_DELAYS = "numDelays";
	/** DataBead property name: How many milliseconds per delay. */
	public static final String DELAY_MS = "delayMs";
	/** DataBead property name: degree of stereo spread (0=none, 1=total). */
	public static final String SPREAD = "spread";
	/** DataBead property name: Gain of the first delay. */
	public static final String FIRST_DELAY_GAIN = "firstDelayGain";
	/** DataBead property name: How much the delay decreases per delay. */
	public static final String GAIN_DROP = "gainDrop";

	// Used by AddFlanger
	/** DataBead property name: Frequency (rate at which the flanger's delay changes). */
	public static final String FREQ_HZ = "freqHz";
	/** DataBead property name: Minimum delay in milliseconds. */
	public static final String MIN_DELAY_MS = "minDelayMs";
	/** DataBead property name: Maximum delay in milliseconds. */
	public static final String MAX_DELAY_MS = "maxDelayMs";
	/** DataBead property name: Gain for original signal through the flanger's comb filter. */
	public static final String A = "a";
	/** DataBead property name: Gain for the delayed signal through the flanger's comb filter. */
	public static final String G = "g";
	/** DataBead property name: Gain for the feed-forward component of the flanger's comb filter. */
	public static final String H = "h";
}
