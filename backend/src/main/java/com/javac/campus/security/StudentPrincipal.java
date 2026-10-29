package com.javac.campus.security;

import java.io.Serializable;

public record StudentPrincipal(Long id, String email, long credentialVersion)
    implements Serializable {}
