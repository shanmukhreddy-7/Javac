# Java entry point

JavacApplication.main(String[] args) starts Spring Boot. Component scanning loads controllers and services under com.javac.campus; entity/repository scanning loads the persistence layer.

Explain the request path: controller → service → repository/JdbcTemplate → database. MarketplaceService owns business rules and transactions. The main method starts the app; it does not contain all business logic.
