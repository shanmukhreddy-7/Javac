/**
 * Application startup. JavacApplication scans sibling packages and starts Spring Boot; business
 * rules live in the service packages.
 *
 * The JVM starts at JavacApplication.main. Explicit scan annotations include the sibling feature
 * packages. Bootstrapping wires components; actual account, job, and wallet behavior belongs in
 * their services.
 */
package com.javac.campus.main;
