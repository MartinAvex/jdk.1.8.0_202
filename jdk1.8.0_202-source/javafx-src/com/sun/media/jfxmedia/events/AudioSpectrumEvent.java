/*
 * Copyright (c) 2010, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.jfxmedia.events;

import com.sun.media.jfxmedia.effects.AudioSpectrum;

public class AudioSpectrumEvent extends PlayerEvent {
    private AudioSpectrum source;
    private double        timestamp;
    private double        duration;
    private boolean       queryTimestamp;

    /*
     * Value of timestamp will be ignored if queryTimestamp is set true and
     * timestamp will be requested from EventQueueThread when spectrum event is
     * received instead. We do not use -1.0 (GST_CLOCK_TIME_NONE), since
     * GStreamer might send us such events in case if something fails, so we using
     * queryTimestamp to know for sure that we need to ask for timestamp from
     * event queue. Note: Only OSX platfrom sets it true. GStreamer platfrom
     * should not use it unless such usage is tested.
     */
    public AudioSpectrumEvent(AudioSpectrum source, double timestamp,
                              double duration, boolean queryTimestamp) {
        this.source = source;
        this.timestamp = timestamp;
        this.duration = duration;
        this.queryTimestamp = queryTimestamp;
    }

    public final AudioSpectrum getSource() {
        return source;
    }

    public final void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public final double getTimestamp() {
        return timestamp;
    }

    public final double getDuration() {
        return duration;
    }

    public final boolean queryTimestamp() {
        return queryTimestamp;
    }
}
