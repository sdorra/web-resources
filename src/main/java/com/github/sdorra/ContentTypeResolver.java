/**
 * The MIT License
 * Copyright (c) 2018 Sebastian Sdorra
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.sdorra;

/**
 * Detects the content type of a path. The {@link ContentTypeResolver} uses the spotter library, if it is on the
 * classpath. If spotter could not be found, the resolver tries to use apache tika. If spotter and tika could not be
 * found, the {@link java.net.FileNameMap} of the jdk is used.
 */
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

    /**
     * Resolves the content type of the filename.
     *
     * @param filename name of file or path
     *
     * @return content type
     */
    static String resolve(String filename) {
        return instance.detect(filename);
    }

    /**
     * Detects the content type of the filename. This method should not be used, use {@link #resolve(String)} instead.
     *
     * @param filename name of file or path
     *
     * @return content type
     */
    public abstract String detect(String filename);

}
