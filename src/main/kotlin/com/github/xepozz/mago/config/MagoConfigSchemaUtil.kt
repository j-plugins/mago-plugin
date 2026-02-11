package com.github.xepozz.mago.config

import com.github.xepozz.mago.config.reference.ConfigStructure
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project

object MagoConfigSchemaUtil {
    private const val REF = $$"$ref"
    private const val DEFS = $$"$defs"
    private const val DEFS_PREFIX = $$"#/$defs/"

    /**
     * Full schema root (with "properties", "$defs", etc.) from `mago config --schema`, or null if not available.
     */
    fun getSchemaRoot(project: Project): JsonObject? {
        val schemaFile = project.getService(MagoSchemaHolder::class.java).getSchema() ?: return null
        val json = ReadAction.compute<String, RuntimeException> {
            schemaFile.inputStream.use { it.readBytes().decodeToString() }
        } ?: return null
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns a copy of the schema with all `#/$defs/` refs inlined, so path navigation needs no ref handling.
     * Stops at circular refs (reuses the same def object).
     */
    fun dereferenceSchema(root: JsonObject): JsonObject {
        root.getAsJsonObject(DEFS) ?: return root.deepCopy()
        return dereferenceElement(root, root.deepCopy(), mutableSetOf()) as JsonObject
    }

    private fun dereferenceElement(root: JsonObject, el: JsonElement, resolving: MutableSet<String>): JsonElement {
        return when {
            el.isJsonObject -> dereferenceObject(root, el.asJsonObject, resolving)
            el.isJsonArray -> dereferenceArray(root, el.asJsonArray, resolving)
            else -> el
        }
    }

    private fun dereferenceObject(root: JsonObject, obj: JsonObject, resolving: MutableSet<String>): JsonObject {
        val ref = obj.get(REF)?.takeIf { it.isJsonPrimitive }?.asString
        if (ref != null && ref.startsWith(DEFS_PREFIX)) {
            val name = ref.removePrefix(DEFS_PREFIX)
            if (name in resolving) return obj
            val def = root.getAsJsonObject(DEFS)?.getAsJsonObject(name) ?: return obj
            resolving.add(name)
            val out = dereferenceObject(root, def.deepCopy(), resolving)
            resolving.remove(name)
            return out
        }
        val result = JsonObject()
        for ((key, value) in obj.entrySet()) {
            result.add(key, dereferenceElement(root, value, resolving))
        }
        return result
    }

    private fun dereferenceArray(root: JsonObject, arr: JsonArray, resolving: MutableSet<String>): JsonArray {
        val result = JsonArray()
        for (el in arr) {
            result.add(dereferenceElement(root, el, resolving))
        }
        return result
    }

    private fun deepCopy(el: JsonElement): JsonElement =
        when {
            el.isJsonObject -> el.asJsonObject.deepCopy()
            el.isJsonArray -> el.asJsonArray.let { a -> JsonArray().apply { a.forEach { add(deepCopy(it)) } } }
            else -> el
        }

    /**
     * Navigates a dot-separated path (e.g. "guard.perimeter.rules") in a dereferenced schema.
     * For array-typed nodes (e.g. "rules"), returns the item schema so "required" / "description" applied to each entry.
     */
    fun getObjectAtPath(dereferencedRoot: JsonObject, path: String): JsonObject? {
        var current: JsonObject? = dereferencedRoot.getAsJsonObject("properties") ?: return null
        val parts = path.split(".")
        for (i in parts.indices) {
            current = current?.getAsJsonObject(parts[i]) ?: return null
            if (i < parts.lastIndex) {
                current = current.getAsJsonObject("properties") ?: return null
            }
        }
        var result = current ?: return null
        if (result.get("type")?.asString == "array") {
            result = result.getAsJsonObject("items") ?: return null
        }
        return result
    }

    /**
     * Returns section name -> description from the config schema.
     */
    fun getSectionDescriptions(project: Project): Map<String, String> {
        val root = getSchemaRoot(project) ?: return emptyMap()
        val resolved = dereferenceSchema(root)
        val result = mutableMapOf<String, String>()
        for (sectionName in ConfigStructure.STRUCTURE.keys) {
            val obj = getObjectAtPath(resolved, sectionName) ?: continue
            val desc = obj.get("description") ?: continue
            if (desc.isJsonPrimitive) result[sectionName] = desc.asString
        }
        return result
    }
}
