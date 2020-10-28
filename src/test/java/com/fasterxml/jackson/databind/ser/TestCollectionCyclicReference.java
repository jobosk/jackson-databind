package com.fasterxml.jackson.databind.ser;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.BaseMapTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * Simple unit tests to verify that it is possible to handle
 * potentially cyclic structures, as long as object graph itself
 * is not cyclic. This is the case for directed hierarchies like
 * trees and DAGs.
 */
public class TestCollectionCyclicReference
        extends BaseMapTest {
    /*
    /**********************************************************
    /* Helper bean classes
    /**********************************************************
     */

    public void testSerialization() throws Exception {

        Bean sameChild = new Bean(3, null, "sameChild");
        Bean first = new Bean(1, sameChild, "first");
        Bean second = new Bean(2, sameChild, "second");
        sameChild.assignNext(first);

        final ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.HANDLE_CIRCULAR_REFERENCE_INDIVIDUALLY_FOR_COLLECTIONS);

        testSerializationCollection(mapper, new TreeSet<>(Arrays.asList(first, second)));
        //testSerializationEnumSet(mapper, EnumSet.of(addEnum(BeanEnum.class, first), addEnum(BeanEnum.class, second)));
        testSerializationIndexedList(mapper, Arrays.asList(first, second));
        testSerializationIterable(mapper, new PriorityQueue<>(Arrays.asList(first, second)));
        //testSerializationIterator(mapper, Arrays.asList(first, second).iterator()); // TODO Problema
    }

    public void testSerializationCollection(final ObjectMapper mapper, final Collection<Bean> collection)
            throws Exception {
        assertEquals(getExpectedResult(), mapper.writeValueAsString(collection));
    }

    public void testSerializationEnumSet(final ObjectMapper mapper, final EnumSet<?> enumSet)
            throws Exception {
        assertEquals(getExpectedResult(), mapper.writeValueAsString(enumSet));
    }

    public void testSerializationIndexedList(final ObjectMapper mapper, final List<Bean> list) throws Exception {
        assertEquals(getExpectedResult(), mapper.writeValueAsString(list));
    }

    public void testSerializationIterable(final ObjectMapper mapper, final Iterable<Bean> iterable)
            throws Exception {
        assertEquals(getExpectedResult(), mapper.writeValueAsString(iterable));
    }

    public void testSerializationIterator(final ObjectMapper mapper, final Iterator<Bean> iterator)
            throws Exception {
        assertEquals(getExpectedResult(), mapper.writeValueAsString(iterator));
    }

    private String getExpectedResult() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("{\"id\":1,\"name\":\"first\",\"next\":");
        builder.append(
                "{\"id\":3,\"name\":\"sameChild\",\"next\":1}}"); // 1 has been visited this iteration => next is a reference
        builder.append(",{\"id\":2,\"name\":\"second\",\"next\":");
        builder.append(
                "{\"id\":3,\"name\":\"sameChild\",\"next\":"); // 1 has noy been visited this iteration => next is fully serialized
        builder
                .append("{\"id\":1,\"name\":\"first\",\"next\":3}"); // 3 has been visited this iteration => next is a reference
        builder.append("}");
        builder.append("}");
        builder.append("]");
        return builder.toString();
    }

    /*
    /**********************************************************
    /* Types
    /**********************************************************
     */

    @JsonIdentityInfo(
            generator = ObjectIdGenerators.PropertyGenerator.class
            , property = "id"
            , scope = Bean.class
    )
    static class Bean implements Comparable {
        final int _id;
        final String _name;
        Bean _next;

        public Bean(int id, Bean next, String name) {
            _id = id;
            _next = next;
            _name = name;
        }

        public int getId() {
            return _id;
        }

        public Bean getNext() {
            return _next;
        }

        public String getName() {
            return _name;
        }

        public void assignNext(Bean n) {
            _next = n;
        }

        @Override
        public int compareTo(Object o) {
            if (o == null) {
                return -1;
            }
            return o instanceof Bean ? Integer.compare(_id, ((Bean) o).getId()) : 0;
        }
    }
}