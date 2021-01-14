package com.fasterxml.jackson.databind.jsontype.impl;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class TypeNameIdResolver extends TypeIdResolverBase {
    protected final MapperConfig<?> _config;

    /**
     * Mappings from class name to type id, used for serialization.
     * <p>
     * Since lazily constructed will require synchronization (either internal
     * by type, or external)
     */
    protected final ConcurrentHashMap<String, String> _typeToId;

    /**
     * Mappings from type id to JavaType, used for deserialization.
     * <p>
     * Eagerly constructed, not modified, can use regular unsynchronized {@link Map}.
     */
    protected final Map<String, JavaType> _idToType;

    /**
     * @since 2.11
     */
    protected final boolean _caseInsensitive;

    protected TypeNameIdResolver(MapperConfig<?> config, JavaType baseType,
                                 ConcurrentHashMap<String, String> typeToId,
                                 HashMap<String, JavaType> idToType) {
        super(baseType, config.getTypeFactory());
        _config = config;
        _typeToId = typeToId;
        _idToType = idToType;
        _caseInsensitive = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES);
    }

    public static TypeNameIdResolver construct(MapperConfig<?> config, JavaType baseType,
                                               Collection<NamedType> subtypes, boolean forSer, boolean forDeser) {
        // sanity check
        if (forSer == forDeser) throw new IllegalArgumentException();

        final ConcurrentHashMap<String, String> typeToId;
        final HashMap<String, JavaType> idToType;

        if (forSer) {
            // Only need Class-to-id for serialization; but synchronized since may be
            // lazily built (if adding type-id-mappings dynamically)
            typeToId = new ConcurrentHashMap<>();
            idToType = null;
        } else {
            idToType = new HashMap<>();
            // 14-Apr-2016, tatu: Apparently needed for special case of `defaultImpl`;
            //    see [databind#1198] for details: but essentially we only need room
            //    for a single value.
            typeToId = new ConcurrentHashMap<>(4);
        }
        final boolean caseInsensitive = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES);
        populateSerDesMaps(baseType.getRawClass(), subtypes, config, forSer, typeToId, forDeser, idToType
                , caseInsensitive, new HashSet<Class<?>>());
        return new TypeNameIdResolver(config, baseType, typeToId, idToType);
    }

    private static void populateSerDesMaps(final Class<?> baseType, final Collection<NamedType> subtypes
            , final MapperConfig<?> config, final boolean toSer, final ConcurrentHashMap<String, String> serMap
            , final boolean toDes, final HashMap<String, JavaType> desMap, final boolean caseInsensitive
            , final Set<Class<?>> visited) {
        if (subtypes != null) {
            for (NamedType t : subtypes) {
                // no name? Need to figure out default; for now, let's just
                // use non-qualified class name
                Class<?> cls = t.getType();
                if (!visited.contains(cls)) {
                    String id = t.hasName() ? t.getName() : _defaultTypeId(cls);
                    if (toSer && serMap != null) {
                        final String className = cls.getName();
                        if (!serMap.contains(className)) {
                            serMap.put(className, id);
                        }
                    }
                    if (toDes && desMap != null) {
                        // [databind#1983]: for case-insensitive lookups must canonicalize:
                        if (caseInsensitive) {
                            id = id.toLowerCase();
                        }
                        // One more problem; sometimes we have same name for multiple types;
                        // if so, use most specific
                        JavaType prev = desMap.get(id);
                        if (prev != null) { // Can only override if more specific
                            if (cls.isAssignableFrom(prev.getRawClass())) { // nope, more generic (or same)
                                continue;
                            }
                        }
                        final JavaType type = config.constructType(cls);
                        if (!desMap.containsValue(type)) {
                            desMap.put(id, type);
                        }
                    }
                    visited.add(cls);
                }
            }
        }
        List<Class<?>> childClasses = getImplementations(baseType);
        if (!childClasses.isEmpty()) {
            for (Class childClass : childClasses) {
                populateSerDesMaps(
                        childClass
                        , getSubTypes(config, childClass)
                        , config
                        , toSer
                        , serMap
                        , toDes
                        , desMap
                        , caseInsensitive
                        , visited
                );
            }
        }
    }

    private static Collection<NamedType> getSubTypes(MapperConfig<?> config, Class classType) {
        final AnnotatedClass ac = config.introspectClassAnnotations(classType).getClassInfo();
        return config.getSubtypeResolver().collectAndResolveSubtypesByTypeId(config, ac);
    }

    private static List<Class<?>> getImplementations(final Class<?> type) {
        final List<Class<?>> result = new ArrayList<>();
        try {
            ServiceLoader loader = ServiceLoader.load(type);
            for (Iterator iterator = loader.iterator(); iterator.hasNext(); ) {
                result.add((Class<?>) iterator.next().getClass());
            }
        } catch (final Exception e) {
            // TODO Log errors
            //e.printStackTrace();
        } catch (final ServiceConfigurationError sce) {
            // TODO Log errors
            //sce.printStackTrace();
        }
        return result;
    }

    /**
     * If no name was explicitly given for a class, we will just
     * use non-qualified class name
     */
    protected static String _defaultTypeId(Class<?> cls) {
        String n = cls.getName();
        int ix = n.lastIndexOf('.');
        return (ix < 0) ? n : n.substring(ix + 1);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.NAME;
    }

    @Override
    public String idFromValue(Object value) {
        return idFromClass(value.getClass());
    }

    protected String idFromClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        // NOTE: although we may need to let `TypeModifier` change actual type to use
        // for id, we can use original type as key for more efficient lookup:
        final String key = clazz.getName();
        String name = _typeToId.get(key);

        if (name == null) {
            // 29-Nov-2019, tatu: As per test in `TestTypeModifierNameResolution` somehow
            //    we need to do this odd piece here which seems unnecessary but isn't.
            Class<?> cls = _typeFactory.constructType(clazz).getRawClass();
            // 24-Feb-2011, tatu: As per [JACKSON-498], may need to dynamically look up name
            // can either throw an exception, or use default name...
            if (_config.isAnnotationProcessingEnabled()) {
                BeanDescription beanDesc = _config.introspectClassAnnotations(cls);
                name = _config.getAnnotationIntrospector().findTypeName(beanDesc.getClassInfo());
            }
            if (name == null) {
                // And if still not found, let's choose default?
                name = _defaultTypeId(cls);
            }
            _typeToId.put(key, name);
        }
        return name;
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> type) {
        // 18-Jan-2013, tatu: We may be called with null value occasionally
        //   it seems; nothing much we can figure out that way.
        if (value == null) {
            return idFromClass(type);
        }
        return idFromValue(value);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        return _typeFromId(id);
    }

    protected JavaType _typeFromId(String id) {
        // [databind#1983]: for case-insensitive lookups must canonicalize:
        if (_caseInsensitive) {
            id = id.toLowerCase();
        }
        // Now: if no type is found, should we try to locate it by
        // some other means? (specifically, if in same package as base type,
        // could just try Class.forName)
        // For now let's not add any such workarounds; can add if need be
        return _idToType.get(id);
    }

    @Override
    public String getDescForKnownTypeIds() {
        return new TreeSet<String>(_idToType.keySet()).toString();
    }

    /*
    /*********************************************************
    /* Helper methods
    /*********************************************************
     */

    @Override
    public String toString() {
        return String.format("[%s; id-to-type=%s]", getClass().getName(), _idToType);
    }
}
