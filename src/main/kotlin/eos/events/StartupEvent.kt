package eos.events

import eos.Eos

interface StartupEvent {
    fun setupComplete(cache: Eos.Cache?)
}