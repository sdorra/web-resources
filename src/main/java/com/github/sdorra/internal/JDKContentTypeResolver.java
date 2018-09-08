package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;

import java.net.FileNameMap;
import java.net.URLConnection;

public class JDKContentTypeResolver extends ContentTypeResolver {
    @Override
    public String detect(String name) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        return fileNameMap.getContentTypeFor(name);
    }
}
