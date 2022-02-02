package com.playarcanum;

import com.playarcanum.inject.annotations.Singleton;
import lombok.Getter;

@Singleton
public class TestThree {
    @Getter private String test = "test three";
}
