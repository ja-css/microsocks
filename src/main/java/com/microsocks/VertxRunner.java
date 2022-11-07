package com.microsocks;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.util.function.Consumer;

public class VertxRunner {
    public static void runVerticle(Class clazz) {
        String verticleId = clazz.getName();
        Consumer<Vertx> runner = vertx -> {
            try {
                vertx.deployVerticle(verticleId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };

        Vertx vertx = Vertx.vertx(new VertxOptions());
        runner.accept(vertx);
    }
}