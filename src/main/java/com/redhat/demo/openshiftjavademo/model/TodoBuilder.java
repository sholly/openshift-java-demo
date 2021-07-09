package com.redhat.demo.openshiftjavademo.model;

public final class TodoBuilder {
    private String name;
    private String description;
    private Boolean finished;

    private TodoBuilder() {
    }

    public static TodoBuilder aTodo() {
        return new TodoBuilder();
    }

    public TodoBuilder name(String name) {
        this.name = name;
        return this;
    }

    public TodoBuilder description(String description) {
        this.description = description;
        return this;
    }

    public TodoBuilder finished(Boolean finished) {
        this.finished = finished;
        return this;
    }

    public Todo build() {
        Todo todo = new Todo();
        todo.setName(name);
        todo.setDescription(description);
        todo.setFinished(finished);
        return todo;
    }
}
