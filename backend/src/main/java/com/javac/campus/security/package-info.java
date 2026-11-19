/**
 * Request filters and the session principal. These checks apply on the server even when a client
 * bypasses the React interface.
 *
 * StudentPrincipal is the serialized session identity. AccountSessionFilter checks that identity
 * against current account state; AuthRateLimitFilter bounds authentication POSTs locally.
 * SecurityConfig installs these filters in the servlet chain.
 */
package com.javac.campus.security;
