package eos.cargo

import eos.model.InstanceDetails
import java.util.*

class ObjectStorage {
    var objects: MutableMap<String, InstanceDetails>

    init {
        objects = HashMap()
    }
}