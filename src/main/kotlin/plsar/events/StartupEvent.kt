package plsar.events

import plsar.Plsar

interface StartupEvent {
    fun setupComplete(cache: Plsar.Cache?)
}