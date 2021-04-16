package com.redhat.demo.openshiftjavademo.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "todoitems")

public class Todo {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    @Column(columnDefinition = "text")
    private String description;
    private Boolean finished;

    public Todo() {
    }

    public Todo(String name, String description, Boolean finished) {
        this.name = name;
        this.description = description;
        this.finished = finished;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getFinished() {
        return finished;
    }

    public void setFinished(Boolean finished) {
        this.finished = finished;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Todo todo = (Todo) o;
        return Objects.equals(id, todo.id) && Objects.equals(name, todo.name) && Objects.equals(description, todo.description) && Objects.equals(finished, todo.finished);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, finished);
    }

    @Override
    public String toString() {
        return "Todo{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", finished=" + finished +
                '}';
    }
}
