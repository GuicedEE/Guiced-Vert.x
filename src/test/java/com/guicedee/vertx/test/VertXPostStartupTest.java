package com.guicedee.vertx.test;

import com.guicedee.client.IGuiceContext;
import org.junit.jupiter.api.Test;

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