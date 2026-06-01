module com.jobwork {

    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // Spring Boot
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.data.jpa;
    requires spring.tx;

    // JPA / Hibernate
    requires jakarta.persistence;

    // Lombok
    requires static lombok;

    // iText PDF
    requires kernel;
    requires layout;
    requires io;

    // Apache POI
    requires org.apache.poi.ooxml;

    // Open packages to JavaFX FXML loader (reflection)
    opens com.jobwork.controller to javafx.fxml;
    opens com.jobwork.domain     to javafx.base, org.hibernate.orm.core;
    opens com.jobwork.config     to javafx.fxml;

    // Open everything to Spring (needed for @Autowired reflection)
    opens com.jobwork to spring.core, spring.beans, spring.context;
    opens com.jobwork.service    to spring.core;
    opens com.jobwork.repository to spring.core;

    exports com.jobwork;
    exports com.jobwork.controller;
    exports com.jobwork.domain;
    exports com.jobwork.service;
}