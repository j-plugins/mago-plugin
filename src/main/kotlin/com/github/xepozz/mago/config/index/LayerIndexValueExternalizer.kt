package com.github.xepozz.mago.config.index

import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

object LayerIndexValueExternalizer : DataExternalizer<LayerIndexValue> {
    override fun save(out: DataOutput, value: LayerIndexValue) {
        out.writeInt(value.values.size)
        for (v in value.values) {
            out.writeUTF(v)
        }
        out.writeInt(value.offset)
        out.writeInt(value.length)
    }

    override fun read(`in`: DataInput): LayerIndexValue {
        val size = `in`.readInt()
        val list = ArrayList<String>(size)
        repeat(size) { list.plusAssign(`in`.readUTF()) }
        val offset = `in`.readInt()
        val length = `in`.readInt()
        return LayerIndexValue(list, offset, length)
    }
}