package com.guicedee.vertx.spi;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageCodec;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Registry for managing dynamic codecs for the Vertx event bus
 */
@Log4j2
public class CodecRegistry {
    
    /**
     * Map to store registered codec names to prevent duplicate registration
     * Key: codec name, Value: true if registered
     */
    private static final Map<String, Boolean> registeredCodecs = new HashMap<>();

    /**
     * Resets the registered codecs tracker, allowing re-registration on the next context boot.
     */
    public static void reset() {
        registeredCodecs.clear();
    }

    /**
     * Pattern for converting camel case to kebab case
     */
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])|([A-Z])([A-Z][a-z])");
    
    /**
     * Standard Vertx types that don't need custom codecs
     */
    private static final Class<?>[] STANDARD_VERTX_TYPES = {
        String.class,
        Boolean.class,
        boolean.class,
        Integer.class,
        int.class,
        Long.class,
        long.class,
        Double.class,
        double.class,
        Float.class,
        float.class,
        Short.class,
        short.class,
        Byte.class,
        byte.class,
        Character.class,
        char.class,
        io.vertx.core.json.JsonObject.class,
        io.vertx.core.json.JsonArray.class,
        io.vertx.core.buffer.Buffer.class,
        byte[].class
    };
    
    /**
     * Checks if the given class is a standard Vertx type
     *
     * @param clazz The class to check
     * @return true if the class is a standard Vertx type, false otherwise
     */
    public static boolean isStandardVertxType(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        
        for (Class<?> standardType : STANDARD_VERTX_TYPES) {
            if (standardType.equals(clazz)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Converts a camel case string to kebab case
     *
     * @param camelCase The camel case string
     * @return The kebab case string
     */
    public static String toKebabCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "";
        }
        
        // Replace camel case with kebab case (e.g., "camelCase" -> "camel-case")
        // Also handle acronyms (e.g., "UWEServerMessage" -> "uwe-server-message")
        String kebabCase = CAMEL_CASE_PATTERN.matcher(camelCase).replaceAll(match -> {
            // Check which pattern matched
            if (match.group(1) != null && match.group(2) != null) {
                // First pattern: lowercase to uppercase
                return match.group(1) + "-" + match.group(2);
            } else {
                // Second pattern: uppercase to uppercase followed by lowercase
                return match.group(3) + "-" + match.group(4);
            }
        }).toLowerCase();
        return kebabCase;
    }
    
    /**
     * Gets the codec name for a given class
     *
     * @param clazz The class
     * @return The codec name
     */
    public static String getCodecName(Class<?> clazz) {
        if (clazz == null || isStandardVertxType(clazz)) {
            return null;
        }
        
        // Get the simple name of the class (without package)
        String className = clazz.getSimpleName();
        
        // Convert to kebab case
        return toKebabCase(className);
    }

    /**
     * Gets the codec name for a given (possibly generic) type.
     * <p>
     * For a raw {@link Class} this matches {@link #getCodecName(Class)}. For a
     * {@link java.lang.reflect.ParameterizedType} (e.g. {@code List<Dto>}) the type
     * arguments are folded into the name (e.g. {@code "list-dto"}) so that:
     * <ul>
     *   <li>the publish side and the registration side derive the <em>same</em> stable
     *       name from the declared type (rather than the runtime class, where
     *       {@code ArrayList} would never match a {@code List} registration), and</li>
     *   <li>different element types map to different codecs, avoiding a single global
     *       {@code "list"} codec being shared across incompatible payloads.</li>
     * </ul>
     *
     * @param type The type (raw or parameterized)
     * @return The codec name, or {@code null} for standard Vert.x types / unresolvable types
     */
    public static String getCodecName(Type type) {
        if (type == null) {
            return null;
        }
        if (type instanceof Class<?> clazz) {
            return getCodecName(clazz);
        }
        if (type instanceof java.lang.reflect.ParameterizedType pt) {
            if (!(pt.getRawType() instanceof Class<?> raw) || isStandardVertxType(raw)) {
                return null;
            }
            StringBuilder sb = new StringBuilder(toKebabCase(raw.getSimpleName()));
            for (Type arg : pt.getActualTypeArguments()) {
                String argName = typeArgName(arg);
                if (argName != null && !argName.isEmpty()) {
                    sb.append('-').append(argName);
                }
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Derives the kebab-case name fragment for a single type argument, recursing into
     * nested parameterized types and resolving wildcard upper bounds.
     */
    private static String typeArgName(Type arg) {
        if (arg instanceof Class<?> c) {
            return toKebabCase(c.getSimpleName());
        }
        if (arg instanceof java.lang.reflect.ParameterizedType) {
            return getCodecName(arg);
        }
        if (arg instanceof java.lang.reflect.WildcardType w) {
            Type[] upper = w.getUpperBounds();
            if (upper.length > 0) {
                return typeArgName(upper[0]);
            }
        }
        if (arg instanceof java.lang.reflect.GenericArrayType ga) {
            String component = typeArgName(ga.getGenericComponentType());
            return component == null ? null : component + "-array";
        }
        return null;
    }

    /**
     * Creates and registers a codec for the given type if it doesn't already exist
     *
     * @param vertx The Vertx instance
     * @param type The type to create a codec for
     * @return The codec name
     */
    @SuppressWarnings("unchecked")
    public static <T> String createAndRegisterCodec(Vertx vertx, Type type) {
        if (type == null) {
            return null;
        }
        
        // Get the raw class from the type
        Class<T> rawClass;
        if (type instanceof Class) {
            rawClass = (Class<T>) type;
        } else if (type instanceof java.lang.reflect.ParameterizedType) {
            rawClass = (Class<T>) ((java.lang.reflect.ParameterizedType) type).getRawType();
        } else {
            log.warn("Unsupported type: {}", type);
            return null;
        }
        
        // Check if it's a standard Vertx type
        if (isStandardVertxType(rawClass)) {
            return null;
        }
        
        // Get the codec name from the full (possibly parameterized) type so generic
        // payloads keep their element types and produce a stable, collision-free name.
        String codecName = getCodecName(type);
        if (codecName == null) {
            codecName = getCodecName(rawClass);
        }
        if (codecName == null) {
            return null;
        }
        
        // Check if the codec is already registered
        if (registeredCodecs.containsKey(codecName)) {
            log.trace("Codec already registered for type {} with name {}", type.getTypeName(), codecName);
            return codecName;
        }
        
        // Create and register the codec carrying the full generic type
        try {
            MessageCodec<T, T> codec = new DynamicCodec<>(type, codecName);
            vertx.eventBus().registerCodec(codec);
            registeredCodecs.put(codecName, true);
            log.debug("Registered codec for type {} with name {}", type.getTypeName(), codecName);
            return codecName;
        } catch (Exception e) {
            log.error("Error registering codec for type {} with name {}", type.getTypeName(), codecName, e);
            return null;
        }
    }
    
    /**
     * Creates and registers codecs for all event types in the registry
     *
     * @param vertx The Vertx instance
     */
    public static void createAndRegisterCodecsForAllEventTypes(Vertx vertx) {
        log.trace("Creating and registering codecs for all event types");
        
        // Register codecs for consumer reference types
        VertxEventRegistry.getEventConsumerReferenceTypes().forEach((address, type) -> {
            createAndRegisterCodec(vertx, type);
        });
        
        // Register codecs for publisher reference types
        VertxEventRegistry.getEventPublisherKeys().forEach((address, key) -> {
            if (key.getTypeLiteral() != null && key.getTypeLiteral().getType() instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) key.getTypeLiteral().getType();
                java.lang.reflect.Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    java.lang.reflect.Type referenceType = typeArgs[0];
                    createAndRegisterCodec(vertx, referenceType);
                }
            }
        });
    }
}