package eos.cargo

import eos.model.Element
import java.util.*

class ElementStorage {
    var elements: MutableMap<String?, Element?>

    init {
        elements = HashMap()
    }
}