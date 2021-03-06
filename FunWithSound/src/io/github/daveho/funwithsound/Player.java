// Copyright 2015-2016, David Hovemeyer <david.hovemeyer@gmail.com>
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.github.daveho.funwithsound;

import io.github.daveho.gervill4beads.CaptureMidiMessages;
import io.github.daveho.gervill4beads.GervillUGen;
import io.github.daveho.gervill4beads.Midi;
import io.github.daveho.gervill4beads.MidiMessageAndTimeStamp;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;

import net.beadsproject.beads.core.AudioContext;
import net.beadsproject.beads.core.Bead;
import net.beadsproject.beads.core.UGen;
import net.beadsproject.beads.data.Sample;
import net.beadsproject.beads.data.SampleManager;
import net.beadsproject.beads.ugens.DelayTrigger;
import net.beadsproject.beads.ugens.Gain;
import net.beadsproject.beads.ugens.RecordToFile;

/**
 * Play a composition.
 */
public class Player {
	// Delay before starting playback,
	// to avoid glitches in the early audio.
	// (I think the GervillUGens are sending junk audio
	// early on, prior to the first MIDI event.)
	private static final long DEFAULT_START_DELAY_US = 2000000L;
	
	// Shut down this many microseconds after the last note off message.
	private static final long DEFAULT_IDLE_WAIT_US = 2000000L;
	
	private Composition composition;
	private AudioContext ac;
	private Gain masterGain;
	private HashMap<String, Soundbank> soundBanks;
	private RealizedInstrument liveSynth;
	private Map<Instrument, RealizedInstrument> instrMap;
	private long startDelayUs;
	private long idleWaitUs;
	private long idleTimeUs;
	private CountDownLatch latch;
	private ArrayList<MidiMessageAndTimeStamp> capturedEvents;
	private MidiDevice device;
	private boolean playing;
	private CustomInstrumentFactory customInstrumentFactory;
	private Soundbank emergency;
	private List<NoteEvent> noteEvents;
	private NoteEventCallback noteEventCallback;
	
	/**
	 * Constructor.
	 */
	public Player() {
		soundBanks = new HashMap<String, Soundbank>();
		instrMap = new IdentityHashMap<Instrument, RealizedInstrument>();
		customInstrumentFactory = new CustomInstrumentFactory() {
			@Override
			public RealizedInstrument create(int code, AudioContext ac) {
				throw new RuntimeException("No custom instrument factory is registered!");
			}
		};
		startDelayUs = DEFAULT_START_DELAY_US;
		idleWaitUs = DEFAULT_IDLE_WAIT_US;
	}
	
	/**
	 * Set the {@link CustomInstrumentFactory} to use for creating the
	 * runtime support for custom instruments.
	 * 
	 * @param customInstrumentFactory the {@link CustomInstrumentFactory} to set
	 */
	public void setCustomInstrumentFactory(CustomInstrumentFactory customInstrumentFactory) {
		this.customInstrumentFactory = customInstrumentFactory;
	}
	
	/**
	 * Set the start delay (in microseconds).
	 * 
	 * @param startDelayUs the start delay (in microseconds)
	 */
	public void setStartDelayUs(long startDelayUs) {
		this.startDelayUs = startDelayUs;
	}
	
	/**
	 * Set the idle wait time (in microseconds).
	 * This is the time between the last note off event
	 * and shutting down the AudioContext.
	 * 
	 * @param idleWaitUs the idle wait time (in microseconds)
	 */
	public void setIdleWaitUs(long idleWaitUs) {
		this.idleWaitUs = idleWaitUs;
	}

	/**
	 * Get the current timestamp in microseconds from the
	 * AudioContext.
	 * 
	 * @return the current timestamp in microseconds
	 */
	public long getCurrentTimestamp() {
		double timeMs = ac.getTime();
		return (long) (timeMs * 1000.0);
	}
	
	/**
	 * Check whether the player is playing asynchronously.
	 * 
	 * @return true if the player is playing, false if not
	 */
	public boolean isPlaying() {
		return playing;
	}
	
	/**
	 * Set the {@link Composition} to play.
	 * 
	 * @param composition the {@link Composition} to play.
	 */
	public void setComposition(Composition composition) {
		this.composition = composition;
	}
	
	/**
	 * Set a {@link NoteEventCallback}.
	 * The callback's {@link NoteEventCallback#onNoteEvent(NoteEvent)} method will
	 * be called (approximately) when {@link NoteEvent}s occur.
	 * Be aware that the callback will occur in the context of the
	 * AudioContext thread.
	 * 
	 * @param noteEventCallback the {@link NoteEventCallback} to set
	 */
	public void setNoteEventCallback(NoteEventCallback noteEventCallback) {
		this.noteEventCallback = noteEventCallback;
	}
	
	private RealizedInstrument createGervill(Instrument instrument) throws MidiUnavailableException, IOException {
		// Note that the GervillUGen isn't connected to an effects chain,
		// or the AudioContext output, at this point.
		GervillUGen gervill = new GervillUGen(ac, Collections.<String, Object>emptyMap());
		RealizedInstrument info = new RealizedInstrument(gervill);
		Synthesizer synth = gervill.getSynth();
		if (instrument.hasSoundFont()) {
			Soundbank sb = getSoundBank(instrument);
			if (sb != null) {
				synth.loadAllInstruments(sb);
			} else {
				System.err.println("Warning: couldn't load soundfont " + instrument.getSoundFont());
			}
		} else  {
			// The built-in JDK implementation of Gervill doesn't seem
			// to create/find the emergency soundbank reliably.  I'm
			// guessing this is due to the way we're instantiating
			// SoftSynthesizer by reflection rather than going through
			// MidiSystem.  In any case, we can just use reflection to
			// create the emergency soundbank in memory.
			Soundbank emergency = getEmergencySoundbank();
			if (emergency == null) {
				synth.loadAllInstruments(emergency);
			}
		}
		int patch = instrument.getPatch();
		if (patch >= 1) {
			// The MIDI patches are numbered 1..128, but encoded as 0..127
			patch--;
			ShortMessage programChange = Midi.createShortMessage(ShortMessage.PROGRAM_CHANGE, patch);
			info.source.send(programChange, -1L);
		}
		return info;
	}

	private Soundbank getEmergencySoundbank() {
		if (this.emergency != null) {
			return this.emergency;
		}
		try {
			String esbClsName = "com.sun.media.sound.EmergencySoundbank";
			Class<?> esbCls = Class.forName(esbClsName);
			Method[] methods = esbCls.getDeclaredMethods();
			for (Method m : methods) {
				if (m.getName().equals("createSoundbank")) {
					System.out.print("Creating emergency soundbank...");
					System.out.flush();
					Soundbank sb = (Soundbank) m.invoke(null);
					System.out.println("done");
					this.emergency = sb;
					return sb;
				}
			}
			System.out.println("Could not find createSoundbank method in " + esbClsName);
			return null;
		} catch (Exception e) {
			System.out.println("Warning: could not create emergency soundbank: " + e.toString());
			return null;
		}
	}

	/**
	 * Play the composition synchronously.
	 * 
	 * @throws MidiUnavailableException if a MIDI synthesizer and/or receiver can't be found
	 * @throws IOException if a soundfont can't be loaded
	 */
	public void play() throws MidiUnavailableException, IOException {
		prepareToPlay();
		playLiveAndWait();
		onPlayingFinished();
	}
	
	/**
	 * Start playing the composition asynchronously.
	 */
	public void startPlaying() {
		try {
			prepareToPlay();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		ac.start();
		this.playing = true;
	}

	/**
	 * Check whether asynchronous playing has ended.
	 */
	public void checkForEndOfPlaying() {
		if (playing && latch.getCount() == 0) {
			onPlayingFinished();
			playing = false;
		}
	}

	/**
	 * Force asynchronous playing to stop.
	 */
	public void forceStopPlaying() {
		if (playing) {
			if (latch.getCount() > 0) {
				ac.stop();
			}
			onPlayingFinished();
			playing = false;
		}
	}

	private void onPlayingFinished() {
		// If we opened a MIDI device, close it
		if (device != null) {
			device.close();
		}
		
		// If MIDI messages were captured, translate them to
		// Rhythm and Melody
		if (capturedEvents != null && !capturedEvents.isEmpty()) {
			analyzeCapturedEvents(capturedEvents);
		}
	}

	private void playLiveAndWait() {
		// Start the AudioContext! (for real-time output)
		ac.start();
		// Wait for playback to complete, then stop the AudioContext
		try {
			latch.await();
		} catch (InterruptedException e) {
			System.out.println("Interrupted waiting for playback to complete");
		}
		System.out.println("Playback finished");
	}
	
	/**
	 * Save the rendered composition as a wave file.
	 * 
	 * @param outputFile the name of the wave file to write
	 * @throws MidiUnavailableException if a MIDI synthesizer and/or receiver can't be found
	 * @throws IOException if a soundfont can't be loaded
	 */
	public void saveWaveFile(String outputFile) throws MidiUnavailableException, IOException {
		prepareToPlay();
		renderToOutputFile(outputFile);
		onPlayingFinished();
	}

	private void renderToOutputFile(String outputFile) throws IOException {
		System.out.print("Saving audio data to " + outputFile + "...");
		System.out.flush();
		File f = new File(outputFile);
		RecordToFile recorder = new RecordToFile(ac, 2, f);
		recorder.addInput(ac.out);
		ac.out.addDependent(recorder);
		// Render to file
		ac.logTime(true);
		ac.runForNMillisecondsNonRealTime(idleTimeUs / 1000L);
		recorder.kill(); // Ensure that all file output is written
		System.out.println("done!");
	}

	/**
	 * Prepare the Beads AudioContext to play the composition and
	 * (if there is one) the live audition part.
	 * Subclasses may override.
	 * 
	 * @throws MidiUnavailableException
	 * @throws IOException
	 */
	protected void prepareToPlay() throws MidiUnavailableException, IOException {
		// Create an AudioContext
		this.ac = new AudioContext();

		// Prepare to capture NoteEvents
		this.noteEvents = new ArrayList<NoteEvent>();
		
		// Create instruments, schedule MidiMessages to be sent to instruments
		this.idleTimeUs = prepareComposition();
		System.out.printf("Idle time at %d us\n", this.idleTimeUs);

		// Register a shutdown hook to detect when playback is finished
		this.latch = new CountDownLatch(1); 
		addShutdownHook(idleTimeUs);
		
		// Register a pre-frame hook to invoke note and beat callbacks
		addPreFrameHook();
		
		// If there is a live instrument, create a synthesizer for it,
		// and arrange to feed live midi events to it
		prepareForAudition();

		// Add gain events
		addGainEvents();
		
		// Prepare instrument effects (and connect the GervillUGens
		// to the AudioContext's output)
		prepareInstrumentsAndEffects();
		
		// Initially, mute the master Gain
		System.out.println("Muting!");
		masterGain.setGain(0.0f);

		// Create a DelayTrigger to unmute the master Gain
		// once the start delay has elapsed
		Bead unmute = new Bead() {
			@Override
			protected void messageReceived(Bead message) {
				System.out.println("Unmuting!");
				masterGain.setGain(1.0f);
			}
		};
		DelayTrigger unmuteTrigger = new DelayTrigger(ac, startDelayUs/1000.0, unmute);
		ac.out.addDependent(unmuteTrigger);
	}

	private void prepareForAudition() throws MidiUnavailableException,
			IOException {
		// Check the composition to see if there is an audition part
		Instrument liveInstr = composition.getAudition();
		if (liveInstr == null) {
			return;
		}
		
		this.device = null;
		this.capturedEvents = new ArrayList<MidiMessageAndTimeStamp>();
		
		// Create a message source to feed MIDI events to the Gervill instance
		createMessageSource(liveInstr);
		
		// Find a MIDI transmitter and feed its generated MIDI events to
		// the message source
		try {
			device = CaptureMidiMessages.getMidiInput(liveSynth.source);
		} catch (MidiUnavailableException e) {
			System.out.println("Warning: no MIDI input device found for live audition");
		}
	}

	private void createMessageSource(final Instrument liveInstr) throws MidiUnavailableException,
			IOException {
		this.liveSynth = getInstrumentInfo(liveInstr);

		// Filter incoming MidiMessages to:
		// - change to channel 10 (if this is a percussion instrument)
		// - add them to capturedEvents list
		final Receiver delegate = liveSynth.source;
		liveSynth.source = new Receiver() {
			@Override
			public void send(MidiMessage message, long timeStamp) {
				if (liveInstr.getType() == InstrumentType.MIDI_PERCUSSION) {
					// Percussion messages should on channel 10
					if (message instanceof ShortMessage) {
						ShortMessage smsg = (ShortMessage) message;
						message = Midi.createShortMessage(smsg.getStatus()|9, smsg.getData1(), smsg.getData2());
					}
				}

				capturedEvents.add(new MidiMessageAndTimeStamp(message, timeStamp));
				
				delegate.send(message, timeStamp);
			}
			
			@Override
			public void close() {
				delegate.close();
			}
		};
	}
	
	/**
	 * Get the Receiver that will deliver MIDI messages to
	 * the Gervill instance being used to play the live audition part.
	 * 
	 * @return the Receiver, or null if there is no live audition part
	 */
	public Receiver getReceiver() {
		return liveSynth != null ? liveSynth.source : null;
	}

	private void addGainEvents() throws MidiUnavailableException, IOException {
		// Distribute GainEvents by instrument
		for (GainEvent e : composition.getGainEvents()) {
			RealizedInstrument info = getInstrumentInfo(e.instr);
			info.gainEvents.add(e);
		}
		
		// Sort the GainEvents by timestamp for each instrument
		for (Map.Entry<Instrument, RealizedInstrument> entry : instrMap.entrySet()) {
			Collections.sort(entry.getValue().gainEvents, new Comparator<GainEvent>() {
				@Override
				public int compare(GainEvent o1, GainEvent o2) {
					if (o1.ts < o2.ts) {
						return -1;
					} else if (o1.ts > o2.ts) {
						return 1;
					} else {
						return 0;
					}
				}
			}); 
		}
	}
	
	private void prepareInstrumentsAndEffects() {
		// Create a "master gain".  For now, this is just used
		// to mute the RealizedInstruments during the start delay.
		// Eventually we can make this controllable (for things
		// like fade-in and fade-out.)
		this.masterGain = new Gain(ac, 2);
		
		for (Map.Entry<Instrument, RealizedInstrument> entry : instrMap.entrySet()) {
			RealizedInstrument info = entry.getValue();
			
			List<AddEffect> fx = composition.getEffectsMap().get(entry.getKey());
			if (fx != null) {
				for (AddEffect effect : fx) {
					info.tail = effect.apply(ac, info);
				}
			}
			
			UGen gainEnvelope = new InstrumentGainEnvelope(ac, info.gainEvents);
			info.gain = new Gain(ac, 2, gainEnvelope);
			info.gain.addInput(info.tail);

			masterGain.addInput(info.gain);
		}
		
		ac.out.addInput(masterGain);
	}

	private void addShutdownHook(final long idleTimeUs) {
		ac.invokeAfterEveryFrame(new Bead() {
			@Override
			protected void messageReceived(Bead message) {
				long timestampUs = ((long)ac.getTime()) * 1000L;
				if (timestampUs >= idleTimeUs) {
					// Notify main thread that playback is complete
					latch.countDown();
					
					System.out.println("Ready to shut down?");
					
					// I assume it's OK for a Bead to stop the AudioContext?
					ac.stop();
				}
			}
		});
	}
	
	private void addPreFrameHook() {
		ac.invokeBeforeEveryFrame(new Bead() {
			private int noteEventIndex = 0;
			
			@Override
			protected void messageReceived(Bead message) {
				// Compute end-of-frame time in microseconds
				long endOfFrame = (long)((ac.getTime() + ac.samplesToMs(ac.getBufferSize())) * 1000.0);
				
				// Invoke note callback for any notes that have been schedule to play
				if (noteEventCallback != null) {
					// Find all NoteEvents due to occur before the end of the frame
					while (noteEventIndex < noteEvents.size()) {
						NoteEvent noteEvent = noteEvents.get(noteEventIndex);
						if (noteEvent.timeStamp >= endOfFrame) {
							break;
						}
						noteEventCallback.onNoteEvent(noteEvent);
						noteEventIndex++;
					}
				}
				
				// TODO: beat callback
			}
		});
	}

	private long prepareComposition() throws MidiUnavailableException, IOException {
		// Convert figures to MidiMessages and schedule them to be played
		long lastNoteOffUs = 0L;
		for (PlayFigureEvent e : composition) {
//			System.out.printf("PlayFigureEvent start time=%d\n", e.getStartUs());
			SimpleFigure f = e.getFigure();
			Instrument instrument = f.getInstrument();
			RealizedInstrument info = getInstrumentInfo(instrument);
			Rhythm rhythm = f.getRhythm();
			Melody melody = f.getMelody();
			int n = Math.min(rhythm.size(), melody.size());
			for (int i = 0; i < n; i++) {
				Strike s = rhythm.get(i);
//				System.out.printf("Strike start time=%d, duration=%d\n", s.getStartUs(), s.getDurationUs());
				Chord c = melody.get(i);
				for (Integer note : c) {
					// Percussion events play on channel 10, normal MIDI
					// events play on channel 1.  (Note that 1 is encoded as
					// 0, and 10 is encoded as 9.)
					int channel = instrument.getType() == InstrumentType.MIDI_PERCUSSION ? 9 : 0;
					
					long onTime = startDelayUs + e.getStartUs() + s.getStartUs();
//					System.out.printf("Note on at %d\n", onTime);
					long offTime = onTime + s.getDurationUs();
					ShortMessage noteOn = Midi.createShortMessage(ShortMessage.NOTE_ON|channel, note, s.getVelocity());
					info.source.send(noteOn, onTime);
					noteEvents.add(new NoteEvent(noteOn, onTime, instrument));
					ShortMessage noteOff = Midi.createShortMessage(ShortMessage.NOTE_OFF|channel, note, s.getVelocity());
					info.source.send(noteOff, offTime);
					noteEvents.add(new NoteEvent(noteOff, offTime, instrument));
					// Keep track of the time of the last note off event
					if (offTime > lastNoteOffUs) {
						lastNoteOffUs = offTime;
					}
				}
			}
		}
		
		// Sort NoteEvents by timestamp
		Collections.sort(noteEvents, NoteEvent.TIMESTAMP_COMPARATOR);

		// Determine idle time
		final long idleTimeUs = lastNoteOffUs + this.idleWaitUs;
		return idleTimeUs;
	}

	private RealizedInstrument getInstrumentInfo(Instrument instrument)
			throws MidiUnavailableException, IOException {
		RealizedInstrument info = instrMap.get(instrument);
		if (info == null) {
			if (instrument.isMidi()) {
				info = createGervill(instrument);
			} else if (instrument.getType() == InstrumentType.SAMPLE_BANK) {
				info = createSampleBank(instrument);
			} else if (instrument.getType().isCustom()) {
				info = customInstrumentFactory.create(instrument.getType().getCode(), ac);
			} else {
				throw new RuntimeException("Don't know how to create a " + instrument.getType() + " instrument");
			}
			instrMap.put(instrument, info);
		}
		return info;
	}
	
	private RealizedInstrument createSampleBank(Instrument instr) {
		RealizedInstrument info = instrMap.get(instr);
		if (info == null) {
			SampleBankUGen sb = new SampleBankUGen(ac);
			for (Map.Entry<Integer, SampleInfo> entry : instr.getSampleMap().entrySet()) {
				SampleInfo sampleInfo = entry.getValue();

				Sample sample = SampleManager.sample(sampleInfo.fileName);
				
				if (sampleInfo.startMs >= 0.0) {
					// Range is specified
					SampleRange sr = new SampleRange(sampleInfo.startMs, sampleInfo.endMs);
					sb.addSample(sampleInfo.note, sample, sampleInfo.gain, sr);
				} else {
					// Play entire sample
					sb.addSample(sampleInfo.note, sample, sampleInfo.gain);
				}
			}
			info = new RealizedInstrument(sb, ac);
			instrMap.put(instr, info);
		}
		return info;
	}

	private Soundbank getSoundBank(Instrument instrument) throws IOException {
		System.out.println("Loading soundfont " + instrument.getSoundFont());
		Soundbank sb = null;
		if (!soundBanks.containsKey(instrument.getSoundFont())) {
			File file = new File(instrument.getSoundFont());
			if (file.exists()) {
				try {
					sb = MidiSystem.getSoundbank(file);
				} catch (InvalidMidiDataException e) {
					throw new IOException("Could not load soundbank " + file.getPath(), e);
				}
			}
			soundBanks.put(instrument.getSoundFont(), sb);
		}
		sb = soundBanks.get(instrument.getSoundFont());
		return sb;
	}
	
	static class NoteStart {
		final long ts;
		final int velocity;
		NoteStart(long ts, int velocity) {
			this.ts = ts;
			this.velocity = velocity;
		}
	}

	private void analyzeCapturedEvents(List<MidiMessageAndTimeStamp> capturedEvents) {
		Map<Integer, NoteStart> starts = new HashMap<Integer, NoteStart>();
		
		Rhythm rhythm = new Rhythm();
		Melody melody = new Melody();
		
		Tempo tempo = composition.getTempo();
		
		long baseTs = -1L;
		
		for (MidiMessageAndTimeStamp mmts : capturedEvents) {
			MidiMessage msg = mmts.msg;
			long ts = mmts.timeStamp;
			if (msg instanceof ShortMessage) {
				ShortMessage smsg = (ShortMessage) msg;
				if (smsg.getCommand() == ShortMessage.NOTE_ON) {
					if (baseTs < 0L && ts >= 0L) {
						baseTs = ts;
//						System.out.println("baseTs="+baseTs);
					}
					int note = smsg.getData1();
					int velocity = smsg.getData2();
					starts.put(note, new NoteStart(ts, velocity));
				} else if (smsg.getCommand() == ShortMessage.NOTE_OFF) {
					int note = smsg.getData1();
					NoteStart start = starts.get(note);
					if (start != null) {
						//System.out.printf("baseTs=%d, start.ts=%d, ts=%d\n", baseTs, start.ts, ts);
						Strike s = new Strike(start.ts - baseTs, ts - start.ts, start.velocity);
						rhythm.add(s);
						Chord ch = new Chord();
						ch.add(note);
						melody.add(ch);
					}
				}
			}
		}
		
		// Output captured Rhythm and Melody
		Scale scale = composition.isUsingDefaultScale() ? null : composition.getScale();
		System.out.print("Rhythm rhythm = ");
		System.out.print(ConvertToCode.toCode(rhythm, tempo));
		System.out.println(";");
		System.out.print("Melody melody = ");
		System.out.print(ConvertToCode.toCode(melody, scale));
		System.out.println(";");
	}
}
