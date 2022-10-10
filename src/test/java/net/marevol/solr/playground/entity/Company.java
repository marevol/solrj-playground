package net.marevol.solr.playground.entity;

import org.apache.solr.client.solrj.beans.Field;

public class Company {
    @Field
    public String id;

    @Field("name_t")
    public String name;

    public Company() {
    }

    public Company(final String id, final String name) {
        this.id = id;
        this.name = name;
    }
}
