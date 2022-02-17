package eos.processor

import eos.Eos
import eos.annotate.Events
import eos.model.InstanceDetails
import eos.util.Support
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

class InstanceProcessor(var cache: Eos.Cache?) {

    var support: Support
    var cl: ClassLoader
    lateinit var jarDeps: ArrayList<String>
    var objects: Map<String, InstanceDetails>

    fun run(): InstanceProcessor {
        if (support.isJar) {
            setJarDeps()
            classesJar
        } else {
            var uri: String? = null
            try {
                uri = support.classesUri
            } catch (e: Exception) {
                e.printStackTrace()
            }
            getClasses(uri)
        }
        return this
    }

    private fun setJarDeps(): List<String> {
        jarDeps = ArrayList()
        val entries: Enumeration<JarEntry>? = support?.jarEntries
        do {
            val jarEntry = entries!!.nextElement()
            val path = getPath(jarEntry.toString())
            if (!path.contains("META-INF.maven.")) continue
            val dep = path.substring(14)
            jarDeps?.add(dep)
        } while (entries!!.hasMoreElements())
        return jarDeps
    }

    protected fun isDep(jarEntry: String): Boolean {
        val jarPath = getPath(jarEntry)
        for (dep in jarDeps!!) {
            if (jarPath.contains(dep)) return true
        }
        return false
    }

    //Thank you walen
    private fun getLastIndxOf(nth: Int, ch: String, string: String?): Int {
        var nth = nth
        return if (nth <= 0) string!!.length else getLastIndxOf(
            --nth,
            ch,
            string!!.substring(0, string.lastIndexOf(ch))
        )
    }

    protected fun isWithinRunningProgram(jarEntry: String): Boolean {
        val main = support.main
        val path = main!!.substring(0, getLastIndxOf(1, ".", main) + 1)
        val jarPath = getPath(jarEntry)
        return if (jarPath.contains(path)) true else false
    }

    protected fun isDirt(jarEntry: String): Boolean {
        if (support.isJar &&
            !isWithinRunningProgram(jarEntry) &&
            isDep(jarEntry)
        ) return true
        if (support.isJar && !jarEntry.endsWith(".class")) return true
        if (jarEntry.contains("org/h2")) return true
        if (jarEntry.contains("javax/servlet/http")) return true
        if (jarEntry.contains("package-info")) return true
        if (jarEntry.startsWith("module-info")) return true
        if (jarEntry.contains("META-INF/")) return true
        if (jarEntry.contains("$")) return true
        return if (jarEntry.endsWith("Exception")) true else false
    }//                if(jarEntry.toString().contains("javax"))continue;

    //                if(jarEntry.toString().contains("org/apache/jasper"))continue;
//                if(jarEntry.toString().contains("org/apache/taglibs"))continue;
//                if(jarEntry.toString().contains("org/apache/tools"))continue;
//                if(jarEntry.toString().contains("org/eclipse/"))continue;
//                if(jarEntry.toString().contains("org/h2"))continue;
//was 5
    //todo:
    protected val classesJar: Unit
        protected get() {
            try {
                //todo:
                val jarUriTres = cl.getResource("eos/") //was 5
                val jarPath = jarUriTres.path.substring(5, jarUriTres.path.indexOf("!"))
                val file = JarFile(jarPath)
                val jarFile: Enumeration<*> = file.entries()
                while (jarFile.hasMoreElements()) {
                    val jarEntry = jarFile.nextElement() as JarEntry
                    if (jarEntry.isDirectory) {
                        continue
                    }
                    if (isDirt(jarEntry.toString())) continue

//                if(jarEntry.toString().contains("javax"))continue;
//                if(jarEntry.toString().contains("org/apache/jasper"))continue;
//                if(jarEntry.toString().contains("org/apache/taglibs"))continue;
//                if(jarEntry.toString().contains("org/apache/tools"))continue;
//                if(jarEntry.toString().contains("org/eclipse/"))continue;
//                if(jarEntry.toString().contains("org/h2"))continue;
                    val path = getPath(jarEntry.toString())
                    val cls = cl.loadClass(path)
                    if (cls.isAnnotation ||
                        cls.isInterface ||
                        cls.name === this.javaClass.name
                    ) {
                        continue
                    }
                    if (cls.isAnnotationPresent(Events::class.java)) {
                        cache?.events = getObject(cls)
                    }
                    val instanceDetails = getObjectDetails(cls)
                    cache?.objects?.put(instanceDetails?.name!!, instanceDetails)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

    protected fun getClasses(uri: String?) {
        val pathFile = File(uri)
        val files = pathFile.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                getClasses(file.path)
                continue
            }
            try {
                if (isDirt(file.path)) continue
                if (!file.path.endsWith(".class") &&
                    !file.path.endsWith(".java")
                ) continue
                var path = getPath("java", "java", file.path)
                if (file.toString().endsWith(".class")) {
                    path = getPath("class", "classes", file.path)
                }
                val cls = cl.loadClass(path)
                if (cls.isAnnotation ||
                    cls.isInterface ||
                    cls.name === this.javaClass.name
                ) {
                    continue
                }
                if (cls.isAnnotationPresent(Events::class.java)) {
                    cache?.events = getObject(cls)
                }
                val instanceDetails = getObjectDetails(cls)
                cache?.objects?.put(instanceDetails?.name!!, instanceDetails)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    protected fun getPath(path: String): String {
        var path = path
        if (path.startsWith("/")) path = path.replaceFirst("/".toRegex(), "")
        return path
            .replace("\\", ".")
            .replace("/", ".")
            .replace(".class", "")
    }

    protected fun getPath(name: String, key: String, path: String): String {
        val separator = System.getProperty("file.separator")
        val regex = key + "\\" + separator
        val pathParts = path.split(regex.toRegex()).toTypedArray()
        return pathParts[1]
            .replace("\\", ".")
            .replace("/", ".")
            .replace(".$name", "")
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
    protected fun getObjectDetails(cls: Class<*>): InstanceDetails {
        val instanceDetails = InstanceDetails()
        instanceDetails.instanceClass = cls
        instanceDetails.name = support.getName(cls.name)
        val instance = getObject(cls)
        instanceDetails.instance = instance
        return instanceDetails
    }

    protected fun getObject(cls: Class<*>): Any? {
        var instance: Any? = null
        try {
            instance = cls.getConstructor().newInstance()
        } catch (e: InstantiationException) {
        } catch (e: IllegalAccessException) {
        } catch (e: InvocationTargetException) {
        } catch (e: NoSuchMethodException) {
        }
        return instance
    }

    init {
        support = Support()
        objects = HashMap()
        cl = Thread.currentThread().contextClassLoader
    }
}