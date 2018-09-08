package com.github.sdorra;

public abstract class ContentTypeResolver {

    private static ContentTypeResolver instance;

    static {
        if (exists("com.github.sdorra.spotter.ContentTypes")) {
            instance = create("com.github.sdorra.internal.SpotterContentTypeResolver");
        } else if (exists("")) {
            instance = create("com.github.sdorra.internal.TikaContentTypeResolver");
        } else {
            instance = create("com.github.sdorra.internal.JDKContentTypeResolver");
        }
    }

    private static ContentTypeResolver create(String className) {
        try {
            return (ContentTypeResolver) Class.forName(className).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalStateException("could not create content type resolver: " + className, e);
        }
    }

    private static boolean exists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected ContentTypeResolver() { }

    static String resolve(String name) {
        return instance.detect(name);
    }

    public abstract String detect(String name);

}
