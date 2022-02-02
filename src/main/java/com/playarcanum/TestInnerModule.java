package com.playarcanum;

import com.playarcanum.inject.module.AbstractInjectorModule;

public class TestInnerModule extends AbstractInjectorModule {
    @Override
    public AbstractInjectorModule enable() {
        this.bind(TestThree.class);

        return this;
    }
}
