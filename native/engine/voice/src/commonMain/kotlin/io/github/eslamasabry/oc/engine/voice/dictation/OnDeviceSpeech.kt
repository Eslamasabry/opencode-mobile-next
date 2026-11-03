package io.github.eslamasabry.oc.engine.voice.dictation

/**
 * Plan 10 §17 S1 ("audio never leaves the phone") for the phone's own recognizer: dictation may use only a recognizer
 * that Android guarantees runs on the device (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, API 31+). There is no
 * fallback to the default recognition service, which may send the audio to a server, not even when the on-device one
 * lacks the language. Where no on-device recognizer exists the mic is not offered.
 */
public object OnDeviceSpeech {
    /** The first Android level with `createOnDeviceSpeechRecognizer` (Build.VERSION_CODES.S). */
    public const val MIN_SDK: Int = 31

    /** Whether dictation can run at all: an on-device recognizer exists ([onDeviceAvailable] is asked only on 31+). */
    public fun usable(sdkInt: Int, onDeviceAvailable: () -> Boolean): Boolean = sdkInt >= MIN_SDK && onDeviceAvailable()
}
