package io.github.daveho.funwithsound.demo;

import java.io.IOException;

import javax.sound.midi.MidiUnavailableException;

import io.github.daveho.funwithsound.Composer;
import io.github.daveho.funwithsound.Player;

public abstract class DemoBase extends Composer {
	// Some soundfonts
	
	// Arachno: http://www.arachnosoft.com/main/soundfont.php
	// This is a really excellent general soundfont for the standard
	// GM1 sound set.
	public static final String ARACHNO = "/home/dhovemey/SoundFonts/arachno/Arachno SoundFont - Version 1.0.sf2";
	
	// Roland TR-808: http://www.hammersound.net/hs_soundfonts.html
	// Sampled version of classic TR-808 percussion sounds.
	public static final String TR808 = "/home/dhovemey/SoundFonts/tr808/TR-808 Drums.SF2";
	
	public abstract void create();

	public void play() throws MidiUnavailableException, IOException {
		Player player = new Player();
		player.setComposition(getComposition());
		player.play();
	}
}