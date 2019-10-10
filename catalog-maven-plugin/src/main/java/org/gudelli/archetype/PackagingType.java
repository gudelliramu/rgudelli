package org.gudelli.archetype;

public enum PackagingType {

    POM("pom"), JAR("jar"), MAVEN_PLUGIN("maven-plugin"), EJB("ejb"), WAR("war"), EAR("ear"), RAR("rar"),
    MAVEN_ARCHETYPE("maven-archetype");

    private String value;

    PackagingType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
