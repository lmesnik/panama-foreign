/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.foreign;

import jdk.internal.foreign.ScopeImpl;
import jdk.internal.foreign.memory.BoundedArray;

import java.foreign.annotations.NativeCallback;
import java.foreign.layout.Layout;
import java.foreign.memory.Array;
import java.foreign.memory.Callback;
import java.foreign.memory.LayoutType;
import java.foreign.memory.Pointer;
import java.foreign.memory.Struct;

/**
 * A scope models a unit of resource lifecycle management. It provides primitives for memory allocation, as well
 * as a basic ownership model for allocating resources (e.g. pointers). Each scope has a parent scope (except the
 * global scope, which acts as root of the ownership model).
 * <p>
*  A scope supports two terminal operation: first, a scope
 * can be closed (see {@link Scope#close()}), which implies that all resources associated with that scope can be reclaimed; secondly,
 * a scope can be merged into the parent scope (see {@link Scope#merge()}). After a terminal operation, a scope will no longer be available
 * for allocation.
 * <p>
 * Scope supports the {@link AutoCloseable} interface which enables thread-confided scopes to be used in conjunction
 * with the try-with-resources construct.
 */
public interface Scope extends AutoCloseable {

    /**
     * Allocate region of memory with given {@code LayoutType}.
     * @param <X> the carrier type associated with the memory region to be allocated.
     * @param type the {@code LayoutType}.
     * @return a pointer to the newly allocated memory region.
     */
    <X> Pointer<X> allocate(LayoutType<X> type);

    /**
     * Allocate region of memory with given layout.
     * @param type the layout.
     * @return a pointer to the newly allocated memory region.
     */
    default Pointer<?> allocate(Layout type) {
        return allocate(LayoutType.ofVoid(type));
    }

    /**
     * Allocate region of memory as an array of elements with given {@code LayoutType}. This is effectively the same as:
     * <p>
     *     <code>
     *         allocateArray(type, size).elementPointer()
     *     </code>
     * </p>
     * @param <X> the carrier type associated with the element type of the array to be allocated.
     * @param elementType the {@code LayoutType} of the array element.
     * @param size the array size.
     * @return a pointer to the first element of the array.
     */
    default <X> Pointer<X> allocate(LayoutType<X> elementType, long size) {
        return allocateArray(elementType, size).elementPointer();
    }

    /**
     * Allocate region of memory as an array of elements with given {@code LayoutType}. This is effectively the same as:
     * <p>
     *     <code>
     *         allocate(elementType.array(size)).withLimit()
     *     </code>
     * </p>
     * @param <X> the carrier type associated with the element type of the array to be allocated.
     * @param elementType the {@code LayoutType} of the array element.
     * @param size the array size.
     * @return an array to the first element of the array.
     */
    <X> Array<X> allocateArray(LayoutType<X> elementType, long size);

    /**
     * Allocate region of memory as an array of elements with given layout. The resulting pointer will have no
     * carrier information associated with it. This is effectively the same as:
     * <p>
     *     <code>
     *         allocate(LayoutType.ofVoid(elementType).array(size)).withLimit();
     *     </code>
     * </p>
     * @param elementType the {@code LayoutType} of the array element.
     * @param size the array size.
     * @return an array to the first element of the array.
     */
    default Array<?> allocateArray(Layout elementType, int size) {
        return allocateArray(LayoutType.ofVoid(elementType), size);
    }

    /**
     * Allocate region of memory as an array of elements with given {@code LayoutType}. The array is initialized
     * with the contents of a given Java array.
     * @param <X> the carrier type associated with the element type of the array to be allocated.
     * @param elementType the {@code LayoutType} of the array element.
     * @param init the (Java) array initializer.
     * @return an array to the first element of the array.
     * @throws IllegalArgumentException if the array initializer type is not compatible with the required type.
     */
    default <X> Array<X> allocateArray(LayoutType<X> elementType, Object init) throws IllegalArgumentException {
        int size = (init == null) ? 0 : java.lang.reflect.Array.getLength(init);
        Array<X> arr = allocateArray(elementType, size);
        if (size > 0) {
            BoundedArray.copyFrom(arr, init, size);
        }
        return arr;
    }

    /**
     * Allocate region of memory with given data.
     * @param carrier the carrier type modelling the data.
     * @param <T> the carrier type.
     * @return a new struct instance (of type {@link Struct}).
     * @see Struct
     */
    <T extends Struct<T>> T allocateStruct(Class<T> carrier);

    /**
     * Allocate a function pointer backed by given Java functional interface instance.
     * @param <T> the carrier type.
     * @param funcIntfClass a functional interface class annotated with the {@link NativeCallback} annotation.
     * @param funcIntfInstance an instance of a functional interface.
     * @return a new function pointer (of type {@link T}).
     * @throws IllegalArgumentException if the provided class is not annotated with the {@link NativeCallback} annotation.
     */
    <T> Callback<T> allocateCallback(Class<T> funcIntfClass, T funcIntfInstance) throws IllegalArgumentException;

    /**
     * Allocate a function pointer backed by given Java functional interface instance. This method is equivalent to:
     * <p>
     *     <code>
     *         allocateCallback(inferClass(funcIntfInstance), funcIntfInstance)
     *     </code>
     * </p>
     * Where the {@code inferClass} is a best-effort attempt at extracting the functional interface from the object
     * passed to this method.
     *
     * @param <T> the carrier type.
     * @param funcIntfInstance an instance of a functional interface.
     * @return a new function pointer (of type {@link T}).
     * @throws IllegalArgumentException if no suitable functional interface can be inferred from the provided instance.
     */
    <T> Callback<T> allocateCallback(T funcIntfInstance);

    /**
     * Allocate and initialize a region of memory with given string. Note: this routine adds a terminator to the string
     * meaning that if the input string contain N characters, the size of the allocated region would be N + 1.
     * @param str the string to be allocated.
     * @return a pointer to the newly allocated memory region.
     */
    default Pointer<Byte> allocateCString(String str) {
        return allocateArray(NativeTypes.UINT8, str.concat("\0").getBytes()).elementPointer();
    }

    /**
     * The parent of this scope.
     * @return the parent of this scope.
     */
    Scope parent();

    /**
     * Closes this scope. All associated resources will be freed as a result of this operation.
     * Any existing resources (e.g. pointers) associated with this scope will no longer be accessible.
     *  As this is a terminal operation, this scope will no longer be available for further allocation.
     */
    @Override
    void close();

    /**
     * Copies all resources of this scope to the parent scope. As this is a terminal operation, this scope will no
     * longer be available for further allocation.
     */
    void merge();

    /**
     * Create a scope whose parent is the current scope.
     * @return the new scope.
     */
    Scope fork();

    /**
     * Retrieves the global scope associated with this VM.
     * @return the global scope.
     */
    static Scope globalScope() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(new RuntimePermission("java.foreign.Scope", "globalScope"));
        }
        return ScopeImpl.GLOBAL;
    }
}