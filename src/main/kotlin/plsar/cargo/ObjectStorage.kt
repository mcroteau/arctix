package plsar.cargo

import plsar.model.InstanceDetails
import java.util.*

class ObjectStorage {
    var objects: MutableMap<String, InstanceDetails>

    init {
        objects = HashMap()
    }
}