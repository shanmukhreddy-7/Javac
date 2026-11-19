/**
 * Campus job and wallet workflows. Controllers receive HTTP requests, DTOs validate input shape,
 * and transactional JDBC services enforce permissions and state changes.
 *
 * Read the DTO records for input shape, MarketplaceController for URLs, MarketplaceService for
 * job rules, and WalletService for manually reviewed balances. Persistence uses parameterized
 * JdbcTemplate SQL, not JPA marketplace entities.
 */
package com.javac.campus.marketplace;
