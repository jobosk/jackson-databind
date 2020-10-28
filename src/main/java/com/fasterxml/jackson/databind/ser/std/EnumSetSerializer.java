package com.fasterxml.jackson.databind.ser.std;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;
import java.util.EnumSet;

@SuppressWarnings("serial")
public class EnumSetSerializer
        extends AsArraySerializerBase<EnumSet<? extends Enum<?>>> {
    /**
     * @since 2.6
     */
    public EnumSetSerializer(JavaType elemType) {
        super(EnumSet.class, elemType, true, null, null);
    }

    public EnumSetSerializer(EnumSetSerializer src,
                             BeanProperty property, TypeSerializer vts, JsonSerializer<?> valueSerializer,
                             Boolean unwrapSingle) {
        super(src, property, vts, valueSerializer, unwrapSingle);
    }

    @Override
    public EnumSetSerializer _withValueTypeSerializer(TypeSerializer vts) {
        // no typing for enums (always "hard" type)
        return this;
    }

    @Override
    public EnumSetSerializer withResolved(BeanProperty property,
                                          TypeSerializer vts, JsonSerializer<?> elementSerializer,
                                          Boolean unwrapSingle) {
        return new EnumSetSerializer(this, property, vts, elementSerializer, unwrapSingle);
    }

    @Override
    public boolean isEmpty(SerializerProvider prov, EnumSet<? extends Enum<?>> value) {
        return value.isEmpty();
    }

    @Override
    public boolean hasSingleElement(EnumSet<? extends Enum<?>> value) {
        return value.size() == 1;
    }

    @Override
    public final void serialize(EnumSet<? extends Enum<?>> value, JsonGenerator gen, SerializerProvider provider
            , boolean handleCircularReferencesIndividually) throws IOException {
        final int len = value.size();
        if (len == 1) {
            if (((_unwrapSingle == null)
                    && provider.isEnabled(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED))
                    || (_unwrapSingle == Boolean.TRUE)) {
                serializeContents(value, gen, provider, handleCircularReferencesIndividually);
                return;
            }
        }
        gen.writeStartArray(value, len);
        serializeContents(value, gen, provider, handleCircularReferencesIndividually);
        gen.writeEndArray();
    }

    @Override
    public void serializeContents(EnumSet<? extends Enum<?>> value, JsonGenerator gen, SerializerProvider provider
            , boolean handleCircularReferencesIndividually) throws IOException {
        JsonSerializer<Object> enumSer = _elementSerializer;
        /* Need to dynamically find instance serializer; unfortunately
         * that seems to be the only way to figure out type (no accessors
         * to the enum class that set knows)
         */
        for (Enum<?> en : value) {
            if (handleCircularReferencesIndividually) {
                provider.resetMemoryCircularReference();
            }
            if (enumSer == null) {
                // 12-Jan-2010, tatu: Since enums cannot be polymorphic, let's
                //   not bother with typed serializer variant here
                enumSer = provider.findContentValueSerializer(en.getDeclaringClass(), _property);
            }
            serializeElement(enumSer, en, gen, provider, null, handleCircularReferencesIndividually);
        }
    }
}
