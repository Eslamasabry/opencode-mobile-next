package io.github.eslamasabry.oc.engine.voice.dictation

/**
 * Eligibility check for Android's on-device speech recognizer fallback (`SpeechRecognizer.createOnDeviceSpeechRecognizer`,
 * API 31+, requested with `RecognizerIntent.EXTRA_PREFER_OFFLINE`). There is no fallback to Android's default speech
 * recognition service, not even when the on-device one lacks the language; that system recognition service's own privacy
 * terms apply. Where no on-device recognizer exists and no downloaded speech model is active, the mic is not offered.
 */
public object OnDeviceSpeech {
    /** The first Android level with `createOnDeviceSpeechRecognizer` (Build.VERSION_CODES.S). */
    public const val MIN_SDK: Int = 31

    /** Whether dictation can run at all: an on-device recognizer exists ([onDeviceAvailable] is asked only on 31+). */
    public fun usable(sdkInt: Int, onDeviceAvailable: () -> Boolean): Boolean = sdkInt >= MIN_SDK && onDeviceAvailable()
}
