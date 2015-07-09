package edu.ycp.cs.funwithsound;

/**
 * Tempo determines the absolute tempo (beats per minute) and also,
 * based on the number of beats per measure, helps determine microsecond
 * offsets and durations of events (e.g., {@link Strike}s) occuring
 * within a measure.
 */
public class Tempo {
	private long beatsPerMinute;
	private long beatsPerMeasure;
	private long usPerBeat;
	
	public Tempo(long beatsPerMinute, long beatsPerMeasure) {
		this.beatsPerMinute = beatsPerMinute;
		this.beatsPerMeasure = beatsPerMeasure;
		this.usPerBeat = (60L * 1000000L) / beatsPerMinute;
	}
	
	public long getBeatsPerMinute() {
		return beatsPerMinute;
	}
	
	public long getBeatsPerMeasure() {
		return beatsPerMeasure;
	}
	
	public long getUsPerBeat() {
		return usPerBeat;
	}
	
	/**
	 * Convert a beat number or count into an offset (within a measure)
	 * or duration in microseconds.
	 * 
	 * @param beat the beat number or count
	 * @return offset or duration in microseconds
	 */
	public long beatToUs(float beat) {
		return (long) (beat * usPerBeat);
	}
	
	/**
	 * Determine the offset in microseconds of the beginning of the specified
	 * measure.
	 * 
	 * @param measure the measure
	 * @return the microsecond timestamp of the start of the measure
	 */
	public long measureToUs(int measure) {
		return measure * beatsPerMeasure * usPerBeat;
	}
}
