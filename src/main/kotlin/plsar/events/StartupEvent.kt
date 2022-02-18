package plsar.events

import plsar.PLSAR

interface StartupEvent {
    fun setupComplete(cache: PLSAR.Cache?)
}