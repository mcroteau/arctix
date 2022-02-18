package plsar.events

import plsar.Pulsar

interface StartupEvent {
    fun setupComplete(cache: Pulsar.Cache?)
}