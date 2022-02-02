package com.playarcanum;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

public class TestTwo {
    @Getter private String test = "test two named " + ThreadLocalRandom.current().nextInt();
}
