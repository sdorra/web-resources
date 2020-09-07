# Web-Resources

[![Build Status](https://travis-ci.org/sdorra/web-resources.svg?branch=master)](https://travis-ci.org/sdorra/web-resources)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.sdorra/web-resources.svg)](https://search.maven.org/search?q=a:web-resources%20g:com.github.sdorra)
[![Quality Gates](https://sonarcloud.io/api/project_badges/measure?project=com.github.sdorra%3Aweb-resources&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.github.sdorra%3Aweb-resources)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.github.sdorra%3Aweb-resources&metric=coverage)](https://sonarcloud.io/dashboard?id=com.github.sdorra%3Aweb-resources)

The web-resources library is usable for serving files over http. 
It does the following things for you:

* Detects and set the right Content-Type for the resource  
* Partial caching via Last-Modified, If-Modified-Since and If-Unmodified-Since header
* Partial caching via  ETag, If-Match, If-None-Match header
* Sets Content-Disposition header
* Head request without content
* Optional GZIP compression
* Optional handling Expires header
* Optional handling Cache-Control header

## Usage

Add the latest stable version of to the dependency management tool of your choice.

E.g. for maven:

```xml
<dependency>
    <groupId>com.github.sdorra</groupId>
    <artifactId>web-resources</artifactId>
    <version>x.y.z</version>
</dependency>
```

Use the latest version from maven central: [![Maven Central](https://img.shields.io/maven-central/v/com.github.sdorra/web-resources.svg)](https://search.maven.org/search?q=a:web-resources%20g:com.github.sdorra)

### Example

```java
Path path = Paths.get("myfile.txt");

WebResourceSender.create()
        .withGZIP()
        .withExpires(7, TimeUnit.DAYS)
        .resource(path)
        .send(request, response);
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
