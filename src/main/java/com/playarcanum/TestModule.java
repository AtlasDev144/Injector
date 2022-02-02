package com.playarcanum;

import com.playarcanum.inject.module.AbstractInjectorModule;

public class TestModule extends AbstractInjectorModule {
    @Override
    public AbstractInjectorModule enable() {
        this.bind(Test.class)
                .bind(TestTwo.class, "one")
                .bind(TestTwo.class, "two")

                .bind(new TestInnerModule());

        return this;
    }
}
