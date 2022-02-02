package com.playarcanum;

import com.playarcanum.inject.annotations.Singleton;
import lombok.Getter;

@Singleton
public class Test {
    @Getter private String test = "test singleton";
}
