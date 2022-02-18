package plsar.model.web

import plsar.model.InstanceDetails
import java.lang.reflect.Method
import java.util.*

class EndpointMapping {
    var path: String? = null
    var regexedPath: String? = null
    var verb: String? = null
    var method: Method? = null
    var typeDetails: List<TypeFeature>? = null
    var typeNames: ArrayList<String?>? = null
    var variablePositions: MutableList<Int?>
    var classDetails: InstanceDetails? = null
    var urlBitFeatures: UrlBitFeatures? = null

    init {
        variablePositions = ArrayList()
    }
}