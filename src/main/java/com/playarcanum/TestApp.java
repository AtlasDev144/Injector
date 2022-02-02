package com.playarcanum;

import com.playarcanum.inject.Injector;
import com.playarcanum.inject.annotations.Assisted;
import com.playarcanum.inject.annotations.Inject;

public class TestApp {
    @Inject private Test singleton;
    @Inject(name = "one") private TestTwo one;

    public TestApp(@Assisted TestThree three,
                   @Assisted(name = "two") TestTwo two) {
        singleton.test();
        one.test();
        two.test();
        three.test();

        //TODO don't forget, if don't have any assisted values or don't want to use injector to create classes
        //Get injector reference
        final Injector injector;
        injector.inject(this);

        final TestApp test = injector.construct(TestApp.class);
    }

}
