/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.prism.impl;

import com.sun.prism.PhongMaterial;

public abstract class BasePhongMaterial extends BaseGraphicsResource implements PhongMaterial {

    protected BasePhongMaterial(Disposer.Record disposerRecord) {
        super(disposerRecord);
    }

    @Override
    public boolean isValid() {
        return true;
    }

}
