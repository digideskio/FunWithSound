package edu.ycp.cs.funwithsound;

/**
 * The composer class builds upon the model classes to provide
 * a simple domain-specific language for defining compositions
 * consisting {@link Rhythm} and {@link Melody} objects
 * rendered using {@link Instrument}.  Note that the
 * {@link #setTempo(Tempo)} and {@link #setScale(Scale)}
 * methods must be called before any of the composition methods
 * can be used.  The composer produces a {@link Composition}
 * object as its product.
 */
public class Composer {
	private Tempo tempo;
	private Scale scale;
	private Composition composition;
	
	public Composer() {
		composition = new Composition();
	}
	
	/**
	 * Get the composition.
	 * 
	 * @return the composition
	 */
	public Composition getComposition() {
		return composition;
	}
	
	/**
	 * Set the {@link Tempo} for the composition.
	 * 
	 * @param tempo the tempo
	 */
	public void setTempo(Tempo tempo) {
		this.tempo = tempo;
	}
	
	/**
	 * Set the {@link Scale} for the composition.
	 * 
	 * @param scale the scale
	 */
	public void setScale(Scale scale) {
		this.scale = scale;
	}

	/**
	 * Create a note or notes chosen from the composer's {@link Scale}.
	 * 
	 * @param notes the note(s), which should be in the range 0..6 for a normal
	 *              heptatonic scale, but could be above or below that range
	 *              to select notes in octaves above or below the scale's
	 *              "normal" octave
	 * @return the {@link Chord} representing the note(s)
	 */
	public Chord n(int... notes) {
		Chord chord = new Chord();
		for (int note : notes) {
			chord.add(scale.get(note));
		}
		return chord;
	}

	/**
	 * Create a melody consisting of the specified
	 * sequence of chords.
	 * 
	 * @param chords the chords: can be integers (to select single notes),
	 *        or {@link Chord} objects
	 * @return the melody
	 */
	public Melody m(Object... chords) {
		Melody melody = new Melody();
		for (Object o : chords) {
			if (o instanceof Number) {
				melody.add(n(((Number)o).intValue()));
			} else if (o instanceof Chord) {
				melody.add((Chord)o);
			}
		}
		return melody;
	}
	
	/**
	 * Shift the octave of a note/chord.
	 * 
	 * @param octave the octave: -1 for one octave down, 1 for one octave up, etc.
	 * @param orig   the original note/chord
	 * @return the shifted note/chord
	 */
	public Chord xn(int octave, Chord orig) {
		Chord result = new Chord();
		for (Integer note : orig) {
			result.add(note + 12*octave);
		}
		return result;
	}
	
	/**
	 * Shift the octave a {@link Melody}. 
	 * 
	 * @param octave the octave: -1 for one octave down, 1 for one octave up, etc.
	 * @param orig the melody to shift
	 * @return the shifted melody
	 */
	public Melody xo(int octave, Melody orig) {
		Melody result = new Melody();
		for (Chord ch : orig) {
			result.add(xn(octave, ch));
		}
		return result;
	}

	/**
	 * Create a full-velocity strike.
	 * 
	 * @param beat      offset in beats
	 * @param duration  duration in beats
	 * @return the strike
	 */
	public Strike s(double beat, double duration) {
		return s(beat, duration, 127);
	}
	
	/**
	 * Create a strike with a specified velocity.
	 * Higher velocity generally means a louder note/chord when played.
	 * 
	 * @param beat      offset in beats
	 * @param duration  duration in beats
	 * @param velocity  velocity, in the range 0..127
	 * @return the strike
	 */
	public Strike s(double beat, double duration, int velocity) {
		Strike strike = new Strike();
		strike.setStartUs(tempo.beatToUs(beat));
		strike.setDurationUs(tempo.beatToUs(duration));
		strike.setVelocity(velocity);
		return strike;
	}
	
	/**
	 * Create a full-velocity quarter beat strike.
	 * 
	 * @param beat offset in beats
	 * @return the strike
	 */
	public Strike qs(double beat) {
		return s(beat, .25);
	}

	/**
	 * Create a quarter beat strike with specified velocity.
	 * 
	 * @param beat      offset in beats
	 * @param velocity  velocity, in the range 0..127
	 * @return
	 */
	public Strike qs(double beat, int velocity) {
		return s(beat, .25, velocity);
	}
	
	/**
	 * Create a full-velocity half beat strike.
	 * 
	 * @param beat offset in beats
	 * @return the strike
	 */
	public Strike hs(double beat) {
		return s(beat, .5);
	}
	
	/**
	 * Create a half beat strike with specified velocity.
	 * 
	 * @param beat offset in beats
	 * @param velocity  velocity, in the range 0..127
	 * @return the strike
	 */
	public Strike hs(double beat, int velocity) {
		return s(beat, .5, velocity);
	}
	
	/**
	 * Create a full-velocity full beat strike.
	 * 
	 * @param beat offset in beats
	 * @return the strike
	 */
	public Strike fs(double beat) {
		return s(beat, 1);
	}
	
	/**
	 * Create a full beat strike with specified velocity.
	 * 
	 * @param beat offset in beats
	 * @param velocity  velocity, in the range 0..127
	 * @return the strike
	 */
	public Strike fs(double beat, int velocity) {
		return s(beat, 1, velocity);
	}
	
	/**
	 * Create a full-velocity percussive strike.
	 * 
	 * @param beat offset in beats
	 * @return the strike
	 */
	public Strike p(double beat) {
		return p(beat, 127);
	}
	
	/**
	 * Create a percussive strike with specified velocity.
	 * Higher velocity generally means a louder percussion sound when played.
	 * 
	 * @param beat offset in beats
	 * @param velocity, in the range 0..127
	 * @return the strike
	 */
	public Strike p(double beat, int velocity) {
		Strike strike = new Strike();
		strike.setStartUs(tempo.beatToUs(beat));
		strike.setDurationUs(1000000L/200L); // duration is arbitrarily 5ms
		strike.setVelocity(velocity);
		return strike;
	}
	
	/**
	 * Create a rhythm from a sequence of strikes.
	 * 
	 * @param strikes sequence of strikes
	 * @return a rhythm
	 */
	public Rhythm r(Strike... strikes) {
		Rhythm rhythm = new Rhythm();
		for (Strike s : strikes) {
			rhythm.add(s);
		}
		return rhythm;
	}
	
	/**
	 * Create an instrument.
	 * 
	 * @param patch the instrument's midi patch number
	 * @return the instrument
	 */
	public Instrument i(int patch) {
		return new Instrument(patch);
	}

	/**
	 * Schedule a rhythm/melody to be played on a particular instrument
	 * at a specified measure.
	 *  
	 * @param measure     the measure (0 for first measure, 1 for second, etc.)
	 * @param rhythm      the rhythm
	 * @param melody      the melody
	 * @param instrument  the instrument
	 */
	public void at(int measure, Rhythm rhythm, Melody melody, Instrument instrument) {
		Figure figure = new Figure();
		figure.setRhythm(rhythm);
		figure.setMelody(melody);
		figure.setInstrument(instrument);
		figure.setStartUs(measure * tempo.getBeatsPerMeasure() * tempo.getUsPerBeat());
		composition.add(figure);
	}
}
