/**
 * Account persistence and controlled credential or account-state changes. Profile details belong
 * in the profile package.
 *
 * User stores credentials/account status and inherits timestamps. UserRepository supplies
 * ordinary and locked lookups. Public responses are assembled from DTOs instead of returning
 * this entity with its password hash.
 */
package com.javac.campus.user;
