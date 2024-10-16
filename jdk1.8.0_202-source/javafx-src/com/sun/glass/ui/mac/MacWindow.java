/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.glass.ui.mac;

import com.sun.glass.events.WindowEvent;
import com.sun.glass.events.mac.NpapiEvent;
import com.sun.glass.ui.Cursor;
import com.sun.glass.ui.Pixels;
import com.sun.glass.ui.Screen;
import com.sun.glass.ui.View;
import com.sun.glass.ui.Window;
import com.sun.glass.ui.Window.State;
import java.nio.ByteBuffer;

import java.util.Map;

/**
 * MacOSX platform implementation class for Window.
 */
final class MacWindow extends Window {

    private native static void _initIDs();
    static {
        _initIDs();
    }

    protected MacWindow(Window owner, Screen screen, int styleMask) {
        super(owner, screen, styleMask);
    }
    protected MacWindow(long parent) {
        super(parent);
    }

    @Override native protected long _createWindow(long ownerPtr, long screenPtr, int mask);
    @Override native protected long _createChildWindow(long parent);
    @Override native protected boolean _close(long ptr);
    @Override native protected boolean _setView(long ptr, View view);
    @Override native protected boolean _setMenubar(long ptr, long menubarPtr);
    @Override native protected boolean _minimize(long ptr, boolean minimize);
    @Override native protected boolean _maximize(long ptr, boolean maximize, boolean wasMaximized);
    @Override protected void _setBounds(long ptr,
                                        int x, int y, boolean xSet, boolean ySet,
                                        int w, int h, int cw, int ch,
                                        float xGravity, float yGravity)
    {
        float sx = getPlatformScaleX();
        float sy = getPlatformScaleY();
        if (xSet)    x = Math.round( x / sx);
        if (ySet)    y = Math.round( y / sy);
        if ( w > 0)  w = Math.round( w / sx);
        if ( h > 0)  h = Math.round( h / sy);
        if (cw > 0) cw = Math.round(cw / sx);
        if (ch > 0) ch = Math.round(ch / sy);
        _setBounds2(ptr, x, y, xSet, ySet, w, h, cw, ch, xGravity, yGravity);
    }
    native protected void _setBounds2(long ptr, int x, int y, boolean xSet, boolean ySet, int w, int h, int cw, int ch, float xGravity, float yGravity);
    @Override native protected boolean _setVisible(long ptr, boolean visible);
    @Override native protected boolean _setResizable(long ptr, boolean resizable);

    native private boolean _requestFocus(long ptr);
    @Override protected boolean _requestFocus(long ptr, int event) {
        //TODO: provide reasonable impl for all possible events
        if (event != WindowEvent.FOCUS_LOST) {
            return _requestFocus(ptr);
        }
        return false;
    }

    @Override native protected void _setFocusable(long ptr, boolean isFocusable);
    @Override native protected boolean _setTitle(long ptr, String title);
    @Override native protected void _setLevel(long ptr, int level);
    @Override native protected void _setAlpha(long ptr, float alpha);
    @Override native protected boolean _setBackground(long ptr, float r, float g, float b);
    @Override native protected void _setEnabled(long ptr, boolean enabled);
    @Override native protected boolean _setMinimumSize(long ptr, int width, int height);
    @Override native protected boolean _setMaximumSize(long ptr, int width, int height);

    private ByteBuffer iconBuffer;

    @Override protected void _setIcon(long ptr, Pixels pixels) {

        if (pixels != null) {
            iconBuffer = pixels.asByteBuffer();
            _setIcon(ptr, iconBuffer, pixels.getWidth(), pixels.getHeight());
        } else {
            iconBuffer = null;
            _setIcon(ptr, null, 0, 0);
        }
    }

    private native void _setIcon(long ptr, Object iconBuffer, int width, int height);

    @Override native protected void _toFront(long ptr);
    @Override native protected void _toBack(long ptr);
    @Override native protected void _enterModal(long ptr);
    @Override native protected void _enterModalWithWindow(long dialog, long window);
    @Override native protected void _exitModal(long ptr);

    @Override native protected boolean _grabFocus(long ptr);
    @Override native protected void _ungrabFocus(long ptr);

    @Override native protected int _getEmbeddedX(long ptr);
    @Override native protected int _getEmbeddedY(long ptr);

    protected void notifyMove(final int x, final int y, boolean isMaximized) {
        if (isMaximized() != isMaximized && !isMinimized()) {
            setState(isMaximized ? State.MAXIMIZED : State.NORMAL);
            handleWindowEvent(System.nanoTime(),
                    isMaximized
                            ? WindowEvent.MAXIMIZE
                            : WindowEvent.RESTORE);
        }
        notifyMove(x, y);
    }

    @Override
    protected void notifyResize(int type, int width, int height) {
        width  = Math.round( width * getPlatformScaleX());
        height = Math.round(height * getPlatformScaleY());
        super.notifyResize(type, width, height);
    }

    @Override
    protected void _setCursor(long ptr, Cursor cursor) {
        ((MacCursor)cursor).set();
    }

    @Override
    public void dispatchNpapiEvent(Map eventInfo) {
        NpapiEvent.dispatchCocoaNpapiEvent(this, eventInfo);
    }

    @Override
    protected void _requestInput(long ptr, String text, int type, double width, double height,
                                    double Mxx, double Mxy, double Mxz, double Mxt,
                                    double Myx, double Myy, double Myz, double Myt,
                                    double Mzx, double Mzy, double Mzz, double Mzt) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void _releaseInput(long ptr) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

