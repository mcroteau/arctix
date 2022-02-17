package eos.processor

import com.sun.net.httpserver.HttpExchange
import eos.exception.EosException
import eos.model.Iterable
import eos.model.web.HttpRequest
import eos.model.web.HttpResponse
import eos.web.Pointcut
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.math.BigDecimal
import java.util.*

class UxProcessor {
    val NEWLINE = "\r\n"
    val FOREACH = "<eos:each"

    @Throws(
        EosException::class,
        NoSuchFieldException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class
    )
    fun process(
        pointcuts: Map<String?, Pointcut?>?,
        view: String,
        httpResponse: HttpResponse,
        request: HttpRequest?,
        exchange: HttpExchange
    ): String {
        val entries = Arrays.asList(*view.split("\n".toRegex()).toTypedArray())
        evaluatePointcuts(request, exchange, entries, pointcuts)
        for (a6 in entries.indices) {
            var entryBase = entries[a6]
            if (entryBase.contains("<eos:set")) {
                setVariable(entryBase, httpResponse)
            }
            if (entryBase.contains(FOREACH)) {
                val iterable = getIterable(a6, entryBase, httpResponse, entries)
                val eachOut = StringBuilder()
                //                System.out.println("z" + iterable.getStop() + ":" +  entries.get(iterable.getStop()));
                for (a7 in iterable.pojos!!.indices) {
                    val obj = iterable.pojos!![a7]
                    var ignore: List<Int?> = ArrayList()
                    for (a8 in iterable.start until iterable.stop) {
                        var entry = entries[a8]
                        if (entry.contains("<eos:if condition=")) {
                            ignore = evaluateEachCondition(a8, entry, obj, httpResponse, entries)
                        }
                        if (ignore.contains(a8)) continue
                        if (entry.contains(FOREACH)) {
                            val deepIterable = getIterableObj(a8, entry, obj, entries)
                            val deepEachOut = StringBuilder()
                            iterateEvaluate(a8, deepEachOut, deepIterable, httpResponse, entries)
                            eachOut.append(deepEachOut.toString())
                        }
                        entry = evaluateEntry(0, 0, iterable.field, entry, httpResponse)
                        if (entry.contains("<eos:set")) {
                            setEachVariable(entry, httpResponse, obj)
                        }
                        evaluateEachEntry(entry, eachOut, obj, iterable.field)
                    }
                }
                entries[a6] = eachOut.toString()
                entries[iterable.stop] = ""
                for (a7 in a6 + 1 until iterable.stop) {
                    entries[a7] = ""
                }
            } else {
                if (entryBase.contains("<eos:if condition=")) {
                    evaluateCondition(a6, entryBase, httpResponse, entries)
                }
                entryBase = evaluateEntry(0, 0, "", entryBase, httpResponse)
                entries[a6] = entryBase
            }
        }
        val entriesCleaned = cleanup(entries)
        val output = StringBuilder()
        for (a6 in entriesCleaned.indices) {
            output.append(entriesCleaned[a6] + NEWLINE)
        }
        val finalOut = retrieveFinal(output)
        return finalOut.toString()
    }

    private fun cleanup(entries: MutableList<String>): List<String> {
        for (a6 in entries.indices) {
            val entry = entries[a6]
            if (entry.contains("<eos:if")) entries[a6] = ""
            if (entry.contains("</eos:if>")) entries[a6] = ""
        }
        return entries
    }

    private fun retrieveFinal(eachOut: StringBuilder): StringBuilder {
        val finalOut = StringBuilder()
        val parts = eachOut.toString().split("\n".toRegex()).toTypedArray()
        for (bit in parts) {
            if (bit.trim { it <= ' ' } != "") finalOut.append(bit + NEWLINE)
        }
        return finalOut
    }

    @Throws(
        NoSuchFieldException::class,
        IllegalAccessException::class,
        EosException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class
    )
    private fun iterateEvaluate(
        a8: Int,
        eachOut: StringBuilder,
        iterable: Iterable,
        httpResponse: HttpResponse,
        entries: List<String>
    ) {
        for (a7 in iterable.pojos!!.indices) {
            val obj = iterable.pojos!![a7]
            var ignore: List<Int?> = ArrayList()
            for (a6 in iterable.start until iterable.stop) {
                var entry = entries[a6]
                if (entry.contains("<eos:if condition=")) {
                    ignore = evaluateEachCondition(a8, entry, obj, httpResponse, entries)
                }
                if (ignore.contains(a8)) continue
                if (entry.contains(FOREACH)) continue
                entry = evaluateEntry(0, 0, iterable.field, entry, httpResponse)
                if (entry.contains("<eos:set")) {
                    setEachVariable(entry, httpResponse, obj)
                }
                evaluateEachEntry(entry, eachOut, obj, iterable.field)
            }
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun evaluateEachCondition(
        a8: Int,
        entry: String,
        obj: Any?,
        httpResponse: HttpResponse,
        entries: List<String>
    ): List<Int?> {
        var ignore: List<Int?> = ArrayList()
        val stop = getEachConditionStop(a8, entries)
        val startIf = entry.indexOf("<eos:if condition=")
        val startExpression = entry.indexOf("\${", startIf)
        val endExpression = entry.indexOf("}", startExpression)
        val expressionNite = entry.substring(startExpression, endExpression + 1)
        val expression = entry.substring(startExpression + 2, endExpression)
        val condition = getCondition(expression)
        val bits = expression.split(condition.toRegex()).toTypedArray()
        val subjectPre = bits[0].trim { it <= ' ' }
        val predicatePre = bits[1].trim { it <= ' ' }


        //<eos:if condition="${town.id == organization.townId}">

        //todo:?2 levels
        //todo: switch
        if (subjectPre.contains(".")) {
            val startSubject = subjectPre.indexOf(".")
            val subjectKey = subjectPre.substring(startSubject + 1).trim { it <= ' ' }
            val subjectObj = getValueRecursive(0, subjectKey, obj)
            val subject = subjectObj.toString()
            if (predicatePre == "null") {
                if (subjectObj == null && condition == "!=") {
                    ignore = getIgnoreEntries(a8, stop)
                }
                if (subjectObj != null && condition == "==") {
                    ignore = getIgnoreEntries(a8, stop)
                }
            } else {
                val predicateKeys = predicatePre.split("\\.".toRegex()).toTypedArray()
                val key = predicateKeys[0]
                val field = predicateKeys[1]
                val keyObj = httpResponse[key]
                val fieldObj = keyObj!!.javaClass.getDeclaredField(field)
                fieldObj.isAccessible = true
                val predicate = fieldObj[keyObj].toString()
                if (predicate == subject && condition == "!=") {
                    ignore = getIgnoreEntries(a8, stop)
                }
                if (predicate != subject && condition == "==") {
                    ignore = getIgnoreEntries(a8, stop)
                }
            }
        } else {
            //todo: one key
        }
        val a = entries[a8]
        val b = entries[stop]
        return ignore
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun setVariable(entry: String, httpResponse: HttpResponse) {
        val startVariable = entry.indexOf("variable=\"")
        val endVariable = entry.indexOf("\"", startVariable + 10)
        //music.
        val variableKey = entry.substring(startVariable + 10, endVariable)
        val startValue = entry.indexOf("value=\"")
        val endValue = entry.indexOf("\"", startValue + 7)
        var valueKey: String
        valueKey = if (entry.contains("value=\"\${")) {
            entry.substring(startValue + 9, endValue)
        } else {
            entry.substring(startValue + 7, endValue)
        }
        if (valueKey.contains(".")) {
            valueKey = valueKey.replace("}", "")
            val keys = valueKey.split("\\.".toRegex()).toTypedArray()
            val key = keys[0]
            if (httpResponse.data().containsKey(key)) {
                val obj = httpResponse[key]
                val field = keys[1]
                val fieldObj = obj!!.javaClass.getDeclaredField(field)
                fieldObj.isAccessible = true
                val valueObj = fieldObj[obj]
                val value = valueObj.toString()
                httpResponse[variableKey] = value
            }
        } else {
            httpResponse[variableKey] = valueKey
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun setEachVariable(entry: String, httpResponse: HttpResponse, obj: Any?) {
        val startVariable = entry.indexOf("variable=\"")
        val endVariable = entry.indexOf("\"", startVariable + 10)
        val variableKey = entry.substring(startVariable + 10, endVariable)
        val startValue = entry.indexOf("value=\"")
        val endValue = entry.indexOf("\"", startValue + 7)
        val valueKey: String
        valueKey = if (entry.contains("value=\"\${")) {
            entry.substring(startValue + 9, endValue)
        } else {
            entry.substring(startValue + 7, endValue)
        }
        if (valueKey.contains(".")) {
            val value = getValueRecursive(0, valueKey, obj)
            httpResponse[variableKey] = value.toString()
        } else {
            httpResponse[variableKey] = valueKey
        }
    }

    private fun evaluatePointcuts(
        request: HttpRequest?,
        exchange: HttpExchange,
        entries: MutableList<String>,
        pointcuts: Map<String?, Pointcut?>?
    ) {
        for ((_, pointcut) in pointcuts!!) {
            val key = pointcut?.key //dice:rollem in <dice:rollem> is key
            val open = "<$key>"
            val rabbleDos = "<$key/>"
            val close = "</$key>"
            for (a6 in entries.indices) {
                var entryBase = entries[a6]
                if (entryBase.trim { it <= ' ' }.startsWith("<!--")) entries[a6] = ""
                if (entryBase.trim { it <= ' ' }.startsWith("<%--")) entries[a6] = ""
                if (entryBase.contains(rabbleDos) &&
                    !pointcut!!.isEvaluation
                ) {
                    val output = pointcut.process(request, exchange)
                    if (output != null) {
                        entryBase = entryBase.replace(rabbleDos, output)
                        entries[a6] = entryBase
                    }
                }
                if (entryBase.contains(open)) {
                    val stop = getAttributeClose(a6, close, entries)
                    if (pointcut!!.isEvaluation) {
                        val isTrue = pointcut.isTrue(request, exchange)
                        if (!isTrue) {
                            for (a4 in a6 until stop) {
                                entries[a4] = ""
                            }
                        }
                    }
                    if (!pointcut.isEvaluation) {
                        val output = pointcut.process(request, exchange)
                        if (output != null) {
                            entryBase = entryBase.replace(open, output)
                            entryBase = entryBase.replace(open + close, output)
                            entries[a6] = entryBase
                            for (a4 in a6 + 1 until stop) {
                                entries[a4] = ""
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getAttributeClose(a6: Int, closeKey: String, entries: List<String>): Int {
        for (a5 in a6 until entries.size) {
            val entry = entries[a5]
            if (entry.contains(closeKey)) {
                return a5
            }
        }
        return a6
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class,
        EosException::class,
        NoSuchFieldException::class
    )
    private fun evaluateCondition(a6: Int, entry: String, httpResponse: HttpResponse, entries: MutableList<String>) {
        val stop = getConditionStop(a6, entries)
        val startIf = entry.indexOf("<eos:if condition=")
        val startExpression = entry.indexOf("\${", startIf)
        val endExpression = entry.indexOf("}", startExpression)
        val expressionNite = entry.substring(startExpression, endExpression + 1)
        val expression = entry.substring(startExpression + 2, endExpression)
        val condition = getCondition(expression)
        val parts: Array<String>
        parts = if (condition != "") { //check if condition exists
            expression.split(condition.toRegex()).toTypedArray()
        } else {
            arrayOf(expression)
        }
        if (parts.size > 1) {
            val left = parts[0].trim { it <= ' ' }
            if (left.contains(".")) {
                val predicate = parts[1].trim { it <= ' ' }
                val keys = left.split("\\.".toRegex()).toTypedArray()
                val objName = keys[0]
                if (!keys[1].contains("(")) {
                    val field = keys[1].trim { it <= ' ' }
                    if (httpResponse.data().containsKey(objName)) {
                        if (predicate != null && predicate == "") {
                            val obj = httpResponse[objName]
                            val fieldObj = obj!!.javaClass.getDeclaredField(field)
                            fieldObj.isAccessible = true
                            val valueObj = fieldObj[obj]
                            val type: Type = fieldObj.type
                            val value = valueObj.toString()
                            when (type.typeName) {
                                "java.lang.String" -> {
                                    if (value == predicate && condition == "!=") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                    if (value != predicate && condition == "==") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                }
                                "java.lang.Integer" -> {
                                    val valueInt = Integer.valueOf(value)
                                    val predicateInt = Integer.valueOf(predicate)
                                    if (valueInt == predicateInt && condition == "!=") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                    if (valueInt != predicateInt && condition == "==") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                }
                                "java.lang.Boolean" -> {
                                    val valueBool = java.lang.Boolean.valueOf(value)
                                    val predicateBool = java.lang.Boolean.valueOf(predicate)
                                    if (valueBool == predicateBool && condition == "!=") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                    if (valueBool != predicateBool && condition == "==") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                }
                                "java.math.BigDecimal" -> {
                                    val valueBd = BigDecimal(value)
                                    val predicateBd = BigDecimal(predicate)
                                    if (valueBd == predicateBd && condition == "!=") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                    if (valueBd != predicateBd && condition == "==") {
                                        clearUxPartial(a6, stop, entries)
                                    }
                                }
                                else -> {
                                }
                            }
                        }
                    } else {
                        clearUxPartial(a6, stop, entries)
                    }
                } else {
                    val methodName = keys[1]
                        .replace("(", "")
                        .replace(")", "")
                    if (httpResponse.data().containsKey(objName)) {
                        val obj = httpResponse[objName]
                        val method = obj!!.javaClass.getDeclaredMethod(methodName)
                        val type: Type = method.returnType
                        val subject: Any = method.invoke(obj).toString()
                        if (!isConditionMet(subject.toString(), predicate, condition, type)) {
                            clearUxPartial(a6, stop, entries)
                        }
                    } else {
                        clearUxPartial(a6, stop, entries)
                    }
                }
            } else {
                //if single lookup
                val subject = parts[0].trim { it <= ' ' }
                var predicate = parts[1].trim { it <= ' ' }
                if (httpResponse.data().containsKey(subject)) {
                    if (predicate.contains("''")) predicate = predicate.replace("'".toRegex(), "")
                    val value = httpResponse[subject].toString()
                    if (value == predicate && condition == "!=") {
                        clearUxPartial(a6, stop, entries)
                    }
                    if (value != predicate && condition == "==") {
                        clearUxPartial(a6, stop, entries)
                    }
                } else {
                    if (condition == "!=" &&
                        (predicate == "''" || predicate == "null")
                    ) {
                        clearUxPartial(a6, stop, entries)
                    }
                }
            }
        } else {
            var notTrueExists = false
            var subjectPre = parts[0].trim { it <= ' ' }
            if (subjectPre.startsWith("!")) {
                notTrueExists = true
                subjectPre = subjectPre.replace("!", "")
            }

            //todo : resolve
            if (subjectPre.contains(".")) {
                val keys = subjectPre.split("\\.".toRegex()).toTypedArray()
                val subject = keys[0]
                val startField = subjectPre.indexOf(".")
                val field = subjectPre.substring(startField + 1)
                if (httpResponse.data().containsKey(subject)) {
                    val obj = httpResponse[subject]
                    val fieldObj = obj!!.javaClass.getDeclaredField(field)
                    fieldObj.isAccessible = true
                    val valueObj = fieldObj[obj]
                    val isTrue = java.lang.Boolean.valueOf(valueObj.toString())
                    if (isTrue == true && notTrueExists == true) {
                        clearUxPartial(a6, stop, entries)
                    }
                    if (isTrue == false && notTrueExists == false) {
                        clearUxPartial(a6, stop, entries)
                    }
                } else {
                    if (!notTrueExists) {
                        clearUxPartial(a6, stop, entries)
                    }
                }
            } else {
                if (httpResponse.data().containsKey(subjectPre)) {
                    val obj = httpResponse[subjectPre]
                    val isTrue = java.lang.Boolean.valueOf(obj.toString())
                    if (isTrue == true && notTrueExists == true) {
                        clearUxPartial(a6, stop, entries)
                    }
                    if (isTrue == false && notTrueExists == false) {
                        clearUxPartial(a6, stop, entries)
                    }
                } else {
                    if (!notTrueExists) {
                        clearUxPartial(a6, stop, entries)
                    }
                }
            }
        }
        entries[a6] = ""
        entries[stop] = ""
        entry.replace(expressionNite, "condition issue : '$expression'")
    }

    private fun getIgnoreEntries(a6: Int, stop: Int): List<Int?> {
        val ignore: MutableList<Int?> = ArrayList()
        for (a4 in a6 until stop) {
            ignore.add(a4)
        }
        return ignore
    }

    private fun clearUxPartial(a6: Int, stop: Int, entries: MutableList<String>) {
        for (a4 in a6 until stop) {
            entries[a4] = ""
        }
    }

    @Throws(EosException::class)
    private fun isConditionMet(subject: String, predicate: String?, condition: String, type: Type): Boolean {
        if (type.typeName == "int" || type.typeName == "java.lang.Integer") {
            if (condition == ">") {
                if (Integer.valueOf(subject) > Integer.valueOf(predicate)) return true
            }
            if (condition == "<") {
                if (Integer.valueOf(subject) < Integer.valueOf(predicate)) return true
            }
            if (condition == "==") {
                if (Integer.valueOf(subject) === Integer.valueOf(predicate)) return true
            }
            if (condition == "<=") {
                if (Integer.valueOf(subject) <= Integer.valueOf(predicate)) return true
            }
            if (condition == ">=") {
                if (Integer.valueOf(subject) >= Integer.valueOf(predicate)) return true
            }
        } else {
            throw EosException("integers only covered right now.")
        }
        return false
    }

    private fun getEachConditionStop(a6: Int, entries: List<String>): Int {
        for (a5 in a6 + 1 until entries.size) {
            if (entries[a5].contains("</eos:if>")) return a5
        }
        return a6
    }

    private fun getConditionStop(a6: Int, entries: List<String>): Int {
        var startCount = 1
        var endCount = 0
        for (a5 in a6 + 1 until entries.size) {
            val entry = entries[a5]
            if (entry.contains("</eos:if>")) {
                endCount++
            }
            if (entry.contains("<eos:if condition=")) {
                startCount++
            }
            if (startCount == endCount && entry.contains("</eos:if>")) {
                return a5
            }
        }
        return a6
    }

    private fun getCondition(expression: String): String {
        if (expression.contains(">")) return ">"
        if (expression.contains("<")) return "<"
        if (expression.contains("==")) return "=="
        if (expression.contains(">=")) return ">="
        if (expression.contains("<=")) return "<="
        return if (expression.contains("!=")) "!=" else ""
    }

    private fun retrofit(a6: Int, size: Int, entries: MutableList<String>) {
        for (a10 in a6 until a6 + size + 1) {
            entries[a10] = ""
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun evaluateEachEntry(entry: String, output: StringBuilder, obj: Any?, activeKey: String?) {
        var entry = entry
        if (entry.contains("<eos:each")) return
        if (entry.contains("</eos:each>")) return
        if (entry.contains("<eos:if condition")) return
        if (entry.contains("\${")) {
            val startExpression = entry.indexOf("\${")
            val endExpression = entry.indexOf("}", startExpression)
            val expression = entry.substring(startExpression, endExpression + 1)
            val keys = entry.substring(startExpression + 2, endExpression).split("\\.".toRegex()).toTypedArray()
            if (keys[0] == activeKey) {
                val startField = expression.indexOf(".")
                val endField = expression.indexOf("}")
                val field = expression.substring(startField + 1, endField)
                val valueObj = getValueRecursive(0, field, obj)
                var value = ""
                if (valueObj != null) value = valueObj.toString()
                entry = entry.replace(expression, value)
                val startRemainder = entry.indexOf("\${")
                if (startRemainder != -1) {
                    evaluateEntryRemainder(startExpression, entry, obj, output)
                } else {
                    output.append(entry + NEWLINE)
                }
            }
        } else {
            output.append(entry + NEWLINE)
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun evaluateEntryRemainder(startExpressionRight: Int, entry: String, obj: Any?, output: StringBuilder) {
        var entry = entry
        val startExpression = entry.indexOf("\${", startExpressionRight - 1)
        val endExpression = entry.indexOf("}", startExpression)
        val expression = entry.substring(startExpression, endExpression + 1)
        val startField = expression.indexOf(".")
        val endField = expression.indexOf("}")
        val field = expression.substring(startField + 1, endField)
        val valueObj = getValueRecursive(0, field, obj)
        var value = ""
        if (valueObj != null) value = valueObj.toString()
        entry = entry.replace(expression, value)
        val startRemainder = entry.indexOf("\${", startExpressionRight - value.length)
        if (startRemainder != -1) {
            evaluateEntryRemainder(startExpression, entry, obj, output)
        } else {
            output.append(entry + NEWLINE)
        }
    }

    @Throws(EosException::class, NoSuchFieldException::class, IllegalAccessException::class)
    private fun getIterableObj(a6: Int, entry: String, obj: Any?, entries: List<String>): Iterable {
        val objs: List<Any?>?
        val startEach = entry.indexOf("<eos:each")
        val startIterate = entry.indexOf("in=", startEach)
        val endIterate = entry.indexOf("\"", startIterate + 4) //4 eq i.n.=.".
        val iterableKey = entry.substring(startIterate + 6, endIterate - 1) //in="${ and }
        val iterableFudge = "\${$iterableKey}"
        val startField = iterableFudge.indexOf(".")
        val endField = iterableFudge.indexOf("}", startField)
        val field = iterableFudge.substring(startField + 1, endField)
        val startItem = entry.indexOf("item=", endIterate)
        val endItem = entry.indexOf("\"", startItem + 8)
        val activeField = entry.substring(startItem + 6, endItem)
        objs = getIterableValueRecursive(0, field, obj) as ArrayList<*>?
        val iterable = Iterable()
        val stop = getStopDeep(a6, entries)
        iterable.start = a6 + 1
        iterable.stop = stop
        iterable.pojos = objs
        iterable.field = activeField
        return iterable
    }

    @Throws(EosException::class, NoSuchFieldException::class, IllegalAccessException::class)
    private fun getIterable(a6: Int, entry: String, httpResponse: HttpResponse, entries: List<String>): Iterable {
        var objs: List<Any?> = ArrayList()
        val startEach = entry.indexOf("<eos:each")
        val startIterate = entry.indexOf("in=", startEach)
        val endIterate = entry.indexOf("\"", startIterate + 4) //4 eq i.n.=.".
        val iterableKey = entry.substring(startIterate + 6, endIterate - 1) //in="${ and }
        val startItem = entry.indexOf("item=", endIterate)
        val endItem = entry.indexOf("\"", startItem + 8)
        val activeField = entry.substring(startItem + 6, endItem)
        val expression = entry.substring(startIterate + 4, endIterate + 1)
        if (iterableKey.contains(".")) {
            objs = getIterableInitial(iterableKey, expression, httpResponse)
        } else if (httpResponse.data().containsKey(iterableKey)) {
            objs = httpResponse[iterableKey] as ArrayList<*>
        }
        val iterable = Iterable()
        val stop = getStop(a6 + 1, entries)
        iterable.start = a6 + 1
        iterable.stop = stop
        iterable.pojos = objs
        iterable.field = activeField
        return iterable
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getIterableInitial(iterable: String, expression: String, httpResponse: HttpResponse): List<Any?> {
        val startField = expression.indexOf("\${")
        val endField = expression.indexOf(".", startField)
        val key = expression.substring(startField + 2, endField)
        if (httpResponse.data().containsKey(key)) {
            val obj = httpResponse[key]
            val objList: Any = getIterableRecursive(iterable, expression, obj)
            return objList as ArrayList<*>
        }
        return ArrayList()
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getIterableRecursive(iterable: String, expression: String, objBase: Any?): List<Any> {
        val objs: List<Any> = ArrayList()
        val startField = expression.indexOf(".")
        val endField = expression.indexOf("}")
        val field = expression.substring(startField + 1, endField)
        val obj = getValueRecursive(0, field, objBase)
        return if (obj != null) {
            obj as ArrayList<*>
        } else objs
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getIterableValueRecursive(idx: Int, baseField: String, baseObj: Any?): Any? {
        var idx = idx
        val fields = baseField.split("\\.".toRegex()).toTypedArray()
        if (fields.size > 1) {
            idx++
            val key = fields[0]
            val fieldObj = baseObj!!.javaClass.getDeclaredField(key)
            if (fieldObj != null) {
                fieldObj.isAccessible = true
                val obj = fieldObj[baseObj]
                val start = baseField.indexOf(".")
                val fieldPre = baseField.substring(start + 1)
                if (obj != null) {
                    return getValueRecursive(idx, fieldPre, obj)
                }
            }
        } else {
            val fieldObj = baseObj!!.javaClass.getDeclaredField(baseField)
            if (fieldObj != null) {
                fieldObj.isAccessible = true
                val obj = fieldObj[baseObj]
                if (obj != null) {
                    return obj
                }
            }
        }
        return ArrayList<Any?>()
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun getValueRecursive(idx: Int, baseField: String, baseObj: Any?): Any? {
        var idx = idx
        val fields = baseField.split("\\.".toRegex()).toTypedArray()
        if (fields.size > 1) {
            idx++
            val key = fields[0]
            val fieldObj = baseObj!!.javaClass.getDeclaredField(key)
            fieldObj.isAccessible = true
            val obj = fieldObj[baseObj]
            val start = baseField.indexOf(".")
            val fieldPre = baseField.substring(start + 1)
            if (obj != null) {
                return getValueRecursive(idx, fieldPre, obj)
            }
        } else {
            try {
                val fieldObj = baseObj!!.javaClass.getDeclaredField(baseField)
                fieldObj.isAccessible = true
                val obj = fieldObj[baseObj]
                if (obj != null) {
                    return obj
                }
            } catch (ex: Exception) {
            }
        }
        return null
    }

    @Throws(
        NoSuchFieldException::class,
        IllegalAccessException::class,
        EosException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class
    )
    private fun evaluateEntry(
        idx: Int,
        start: Int,
        activeField: String?,
        entry: String,
        httpResponse: HttpResponse
    ): String {
        var idx = idx
        var entry = entry
        if (entry.contains("\${") &&
            !entry.contains("<eos:each") &&
            !entry.contains("<eos:if")
        ) {
            val startExpression = entry.indexOf("\${", start)
            if (startExpression == -1) return entry
            val endExpression = entry.indexOf("}", startExpression)
            val expression = entry.substring(startExpression, endExpression + 1)
            val fieldBase = entry.substring(startExpression + 2, endExpression)
            if (fieldBase != activeField) {
                if (fieldBase.contains(".")) {
                    val fields = fieldBase.split("\\.".toRegex()).toTypedArray()
                    val key = fields[0]
                    if (httpResponse.data().containsKey(key)) {
                        val obj = httpResponse[key]
                        val startField = fieldBase.indexOf(".")
                        val passiton = fieldBase.substring(startField + 1)

                        //todo: allow for parameters?
                        if (passiton.contains("()")) {
                            val method = passiton.replace("(", "")
                                .replace(")", "")
                            try {
                                val methodObj = obj!!.javaClass.getDeclaredMethod(method)
                                val valueObj = methodObj.invoke(obj)
                                val value = valueObj.toString()
                                entry = entry.replace(expression, value)
                            } catch (ex: Exception) {
                            }
                        } else {
                            val value = getValueRecursive(0, passiton, obj)
                            if (value != null) {
                                entry = entry.replace(expression, value.toString())
                            } else if (activeField == "") {
                                //make empty!
                                entry = entry.replace(expression, "")
                            }
                        }
                    } else if (activeField == "") {
                        //make empty!
                        entry = entry.replace(expression, "")
                    }
                } else {
                    if (httpResponse.data().containsKey(fieldBase)) {
                        val obj = httpResponse[fieldBase]
                        entry = entry.replace(expression, obj.toString())
                    } else if (activeField == "") {
                        entry = entry.replace(expression, "")
                    }
                }
                if (entry.contains("\${")) {
                    idx++
                    if (idx >= entry.length) return entry
                    entry = evaluateEntry(idx, startExpression + idx, activeField, entry, httpResponse)
                }
            }
        }
        return entry
    }

    @Throws(EosException::class)
    private fun invokeMethod(fieldBase: String, obj: Any): String {
        val startMethod = fieldBase.indexOf(".")
        val endMethod = fieldBase.indexOf("(", startMethod)
        val name = fieldBase.substring(startMethod + 1, endMethod)
            .replace("(", "")
        val startSig = fieldBase.indexOf("(")
        val endSig = fieldBase.indexOf(")")
        val paramFix = fieldBase.substring(startSig + 1, endSig)
        val parameters = paramFix.split(",".toRegex()).toTypedArray()
        if (parameters.size > 0) {
            try {
                val method = getObjMethod(name, obj)
                val finalParams = getMethodParameters(method, parameters)
                if (method != null) {
                    return method.invoke(obj, *finalParams.toTypedArray()).toString()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } else {
            try {
                val method = obj.javaClass.getDeclaredMethod(name)
                if (method != null) {
                    return method.invoke(obj).toString()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        return ""
    }

    private fun getObjMethod(methodName: String, obj: Any): Method? {
        val methods = obj.javaClass.declaredMethods
        for (method in methods) {
            val namePre = method.name
            if (namePre == methodName) return method
        }
        return null
    }

    @Throws(EosException::class)
    private fun getMethodParameters(method: Method?, parameters: Array<String>): List<Any?> {
        if (method!!.parameterTypes.size != parameters.size) throw EosException("parameters on $method don't match.")
        val finalParams: MutableList<Any?> = ArrayList()
        for (a6 in parameters.indices) {
            val parameter = parameters[a6]
            val type: Type = method.parameterTypes[a6]
            var obj: Any? = null
            if (type.typeName == "int" || type.typeName == "java.lang.Integer") {
                obj = Integer.valueOf(parameter)
            }
            if (type.typeName == "double" || type.typeName == "java.lang.Double") {
                obj = java.lang.Double.valueOf(parameter)
            }
            if (type.typeName == "java.math.BigDecimal") {
                obj = BigDecimal(parameter)
            }
            if (type.typeName == "float" || type.typeName == "java.lang.Float") {
                obj = java.lang.Float.valueOf(parameter)
            }
            finalParams.add(obj)
        }
        return finalParams
    }

    private fun getStopDeep(idx: Int, entries: List<String>): Int {
        val a10 = idx
        for (a6 in idx until entries.size) {
            val entry = entries[a6]
            if (entry.contains("</eos:each>")) {
                return a6
            }
        }
        return idx
    }

    private fun getStop(a6: Int, entries: List<String>): Int {
        var count = 0
        var startRendered = false
        for (a4 in a6 until entries.size) {
            val entry = entries[a4]
            if (entry.contains("<eos:each")) {
                startRendered = true
            }
            if (!startRendered && entry.contains("</eos:each>")) {
                return a4
            }
            if (startRendered && entry.contains("</eos:each>")) {
                if (count == 1) {
                    return a4
                }
                count++
            }
        }
        return 0
    }
}