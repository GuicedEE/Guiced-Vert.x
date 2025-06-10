package com.guicedee.vertx;

import com.google.inject.Key;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class VertXPostStartupTest
{

    @Test
    void postLoad()
    {
        IGuiceContext context = IGuiceContext.getContext();
        context.inject();
        System.out.println("Done");
    }



}