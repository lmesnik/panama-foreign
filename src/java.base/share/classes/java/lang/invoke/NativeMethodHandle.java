/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.lang.invoke;

import java.util.Arrays;
import jdk.internal.vm.annotation.ForceInline;

import static java.lang.invoke.LambdaForm.*;
import static java.lang.invoke.MethodHandleNatives.Constants.REF_invokeStatic;
import static java.lang.invoke.MethodHandleStatics.newInternalError;
import static java.lang.invoke.MethodTypeForm.*;
import static java.lang.invoke.MethodTypeForm.LF_NEWINVSPECIAL;

/** TODO */
/*non-public*/ class NativeMethodHandle extends MethodHandle {
    final NativeEntryPoint nativeFunc;

    /**
     * TODO
     */
    private NativeMethodHandle(MethodType type, LambdaForm form, NativeEntryPoint nativeFunc) {
        super(type, form);
        this.nativeFunc = nativeFunc;
    }

    /** TODO */
    static NativeMethodHandle make(MethodType type, NativeEntryPoint nativeFunc) {
        LambdaForm lform = preparedLambdaForm(type);
        return new NativeMethodHandle(type, lform, nativeFunc);
    }

    private static final MemberName.Factory IMPL_NAMES = MemberName.getFactory();

    private static LambdaForm preparedLambdaForm(MethodType mtype) {
        int id = MethodTypeForm.LF_INVNATIVE;
        mtype = mtype.basicType(); // TODO: is it correct for native functions to erase types to int?
        LambdaForm lform = mtype.form().cachedLambdaForm(id);
        if (lform != null) return lform;
        lform = makePreparedLambdaForm(mtype);
        return mtype.form().setCachedLambdaForm(id, lform);
    }

    private static LambdaForm makePreparedLambdaForm(MethodType mtype) {
        MethodType mtypeWithArg = mtype.appendParameterTypes(NativeEntryPoint.class);
        MemberName linker = new MemberName(MethodHandle.class, "linkToNative", mtypeWithArg, REF_invokeStatic);
        try {
            linker = IMPL_NAMES.resolveOrFail(REF_invokeStatic, linker, null, NoSuchMethodException.class);
        } catch (ReflectiveOperationException ex) {
            throw newInternalError(ex);
        }
        final int NMH_THIS = 0;
        final int ARG_BASE = 1;
        final int ARG_LIMIT = ARG_BASE + mtype.parameterCount();
        int nameCursor = ARG_LIMIT;
        final int GET_MEMBER = nameCursor++;
        final int LINKER_CALL = nameCursor++;
        LambdaForm.Name[] names = arguments(nameCursor - ARG_LIMIT, mtype.invokerType());
        assert (names.length == nameCursor);
        names[GET_MEMBER] = new LambdaForm.Name(Lazy.NF_internalNativeEntryPoint, names[NMH_THIS]);
        Object[] outArgs = Arrays.copyOfRange(names, ARG_BASE, GET_MEMBER + 1, Object[].class);
        assert (outArgs[outArgs.length - 1] == names[GET_MEMBER]);  // look, shifted args!
        int result = LAST_RESULT;
        names[LINKER_CALL] = new LambdaForm.Name(linker, outArgs);
        LambdaForm lform = new LambdaForm(ARG_LIMIT, names, result);
        // This is a tricky bit of code.  Don't send it through the LF interpreter.
        lform.compileToBytecode();
        return lform;
    }

    final
    @Override
    MethodHandle copyWith(MethodType mt, LambdaForm lf) {
        assert (this.getClass() == NativeMethodHandle.class);  // must override in subclasses
        return new NativeMethodHandle(mt, lf, nativeFunc);
    }

    @Override
    BoundMethodHandle rebind() {
        return BoundMethodHandle.makeReinvoker(this);
    }

    @ForceInline
    static NativeEntryPoint internalNativeEntryPoint(Object mh) {
        return ((NativeMethodHandle)mh).nativeFunc;
    }

    /**
     * Pre-initialized NamedFunctions for bootstrapping purposes.
     * Factored in an inner class to delay initialization until first usage.
     */
    private static class Lazy {
        static Class<NativeMethodHandle> THIS_CLASS = NativeMethodHandle.class;

        static final NamedFunction
                NF_internalNativeEntryPoint;

        static {
            try {
                NamedFunction[] nfs = new NamedFunction[]{
                        NF_internalNativeEntryPoint = new NamedFunction(
                                THIS_CLASS.getDeclaredMethod("internalNativeEntryPoint", Object.class))
                };
                for (NamedFunction nf : nfs) {
                    // Each nf must be statically invocable or we get tied up in our bootstraps.
                    assert (InvokerBytecodeGenerator.isStaticallyInvocable(nf.member)) : nf;
                    nf.resolve();
                }
            } catch (ReflectiveOperationException ex) {
                throw newInternalError(ex);
            }
        }
    }
}