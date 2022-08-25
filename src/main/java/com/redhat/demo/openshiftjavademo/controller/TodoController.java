package com.redhat.demo.openshiftjavademo.controller;

import com.redhat.demo.openshiftjavademo.model.Todo;
import com.redhat.demo.openshiftjavademo.repository.TodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

@RestController
public class TodoController {
    
    private final Logger logger = LoggerFactory.getLogger(TodoController.class);

    @Autowired
    private TodoRepository todoRepository;

    @GetMapping("/todos")
    public List<Todo> getAllTodos() {
        logger.info("In /todos, " + LocalDate.now());
        return todoRepository.findAll();
    }
}
