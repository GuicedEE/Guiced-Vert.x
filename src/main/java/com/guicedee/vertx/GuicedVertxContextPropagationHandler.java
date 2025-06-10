package com.guicedee.vertx;

import com.google.inject.Inject;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedservlets.servlets.services.scopes.CallScope;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GuicedVertxContextPropagationHandler
{
    private final Vertx vertx;
    private static final Logger log = Logger.getLogger(GuicedVertxContextPropagationHandler.class.getName());

    @Inject
    public GuicedVertxContextPropagationHandler(Vertx vertx)
    {
        this.vertx = vertx;
    }

    @Inject
    public void setupContextPropagation() {
        /*
        Infrastructure.setDefaultExecutor(new Executor() {
            @Override
            public void execute(Runnable command) {
                log.info("Mutiny executor called"); // Add logging to verify execution
            
                // Capture CallScoper values from current thread
                Map<?, ?> callScopeValues = null;
                try {
                    CallScoper callScoper = IGuiceContext.get(CallScoper.class);
                    callScopeValues = callScoper.getValues();
                    log.info("Captured call scope values: " + callScopeValues); // Log captured values
                } catch (Exception e) {
                    log.log(Level.FINE, "No CallScope active in this thread", e);
                }

                // Store the final reference for the lambda
                final Map<?, ?> finalCallScopeValues = callScopeValues;
            
                // Create a runnable that will execute in the new thread
                Runnable wrappedCommand = new Runnable() {
                    @Override
                    public void run() {
                        log.info("Executing wrapped command"); // Log when wrapped command starts
                    
                        // Set up CallScoper in the new thread if we have values
                        CallScoper targetCallScoper = null;
                        if (finalCallScopeValues != null) {
                            targetCallScoper = new CallScoper();
                            try {
                                targetCallScoper.enter();
                                targetCallScoper.setValues((Map) finalCallScopeValues);
                                log.info("Set up call scope in target thread"); // Log successful setup
                            } catch (Exception e) {
                                log.log(Level.WARNING, "Error setting up call scope in target thread", e);
                            }
                        }

                        try {
                            // Run the original command
                            command.run();
                        } finally {
                            // Cleanup CallScoper if we created one
                            if (targetCallScoper != null) {
                                try {
                                    if (targetCallScoper.isStartedScope()) {
                                        targetCallScoper.exit();
                                        log.info("Cleaned up call scope"); // Log cleanup
                                    }
                                } catch (Exception e) {
                                    log.log(Level.WARNING, "Error cleaning up call scope", e);
                                }
                            }
                        }
                    }
                };

                // Execute the wrapped command on the Vertx event loop
                log.info("Scheduling wrapped command on Vertx event loop");
                if (Context.isOnEventLoopThread()) {
                    wrappedCommand.run();
                } else {
                    vertx.runOnContext(v -> wrappedCommand.run());
                }
            }
        });*/
        //log.info("Mutiny default executor has been set"); // Log successful setup
    }

    public void storeCallScope(CallScope callScope) {
        Context context = vertx.getOrCreateContext();
        context.put("callScope", callScope);
    }

    public CallScope getCallScope() {
        Context context = vertx.getOrCreateContext();
        return context.get("callScope");
    }
}