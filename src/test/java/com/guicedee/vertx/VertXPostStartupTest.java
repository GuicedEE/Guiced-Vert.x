package com.guicedee.vertx;

import com.guicedee.client.IGuiceContext;
import org.junit.jupiter.api.Test;

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