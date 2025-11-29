package com.guicedee.vertx;

import com.google.inject.Singleton;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.vertx.spi.VertXPreStartup;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Singleton
@Log4j2
public class VertXPostStartup implements IGuicePreDestroy<VertXPostStartup>
{

    @Override
    public void onDestroy()
    {
        if (VertXPreStartup.getVertx() != null)
        {
            VertXPreStartup.getVertx().close();
        }
        if (VertXPreStartup.getVertx() != null)
        {
            VertXPreStartup.getVertx().close();
        }
    }

    @Override
    public Integer sortOrder()
    {
        return Integer.MIN_VALUE + 50;
    }
}
