package com.redhat.demo.openshiftjavademo.controller;

import com.redhat.demo.openshiftjavademo.model.Todo;
import com.redhat.demo.openshiftjavademo.model.TodoBuilder;
import com.redhat.demo.openshiftjavademo.repository.TodoRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RestController
public class TodoController {

    @Autowired
    private TodoRepository todoRepository;

    @GetMapping("/todos")
    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    @GetMapping("/maketodo")
    public ResponseEntity<String> makeTodos() {

        int count = 500000;
        List<Todo> todoList = new ArrayList<>();
        for(int i =0; i < count; i++){

            if((i % 1000) == 0) {
                System.out.println("done + " + i );
            }
            Todo todo = TodoBuilder
                    .aTodo()
                    .finished(false)
                    .name(RandomStringUtils.randomAlphanumeric(20))
                    .description(RandomStringUtils.randomAlphabetic(80))
                    .build();
            todoList.add(todo);
        }
        System.out.println("saving todoList");
        todoRepository.saveAll(todoList);
        todoRepository.flush();
        System.out.println("done saving todoList");


        return ResponseEntity.ok("created");
    }
}
