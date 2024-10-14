/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.webkit.network;

import java.lang.annotation.Native;
import java.nio.ByteBuffer;

abstract class URLLoaderBase {
    @Native public static final int ALLOW_UNASSIGNED = java.net.IDN.ALLOW_UNASSIGNED;

    /**
     * Cancels the loader.
     */
    protected abstract void fwkCancel();

    protected static native void twkDidSendData(long totalBytesSent,
                                              long totalBytesToBeSent,
                                              long data);

    protected static native void twkWillSendRequest(int status,
                                                     String contentType,
                                                     String contentEncoding,
                                                     long contentLength,
                                                     String headers,
                                                     String url,
                                                     long data);

    protected static native void twkDidReceiveResponse(int status,
                                                     String contentType,
                                                     String contentEncoding,
                                                     long contentLength,
                                                     String headers,
                                                     String url,
                                                     long data);

    protected static native void twkDidReceiveData(ByteBuffer byteBuffer,
                                                 int position,
                                                 int remaining,
                                                 long data);

    protected static native void twkDidFinishLoading(long data);

    protected static native void twkDidFail(int errorCode,
                                          String url,
                                          String message,
                                          long data);

}
