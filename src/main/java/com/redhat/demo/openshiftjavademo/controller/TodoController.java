package com.redhat.demo.openshiftjavademo.controller;

import com.redhat.demo.openshiftjavademo.model.Todo;
import com.redhat.demo.openshiftjavademo.repository.TodoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TodoController {

    @Autowired
    private TodoRepository todoRepository;

    @GetMapping("/todos")
    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }
}
